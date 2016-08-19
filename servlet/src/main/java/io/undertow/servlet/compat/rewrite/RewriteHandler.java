/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.compat.rewrite;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.util.Headers;
import io.undertow.util.QueryParameterUtils;

import java.nio.charset.StandardCharsets;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Remy Maucherat
 */
public class RewriteHandler implements HttpHandler {

    private final RewriteConfig config;
    private final HttpHandler next;

    /**
     * If rewriting occurs, the whole request will be processed again.
     */
    protected ThreadLocal<Boolean> invoked = new ThreadLocal<>();

    public RewriteHandler(RewriteConfig config, HttpHandler next) {
        this.config = config;
        this.next = next;
    }


    public void handleRequest(HttpServerExchange exchange) throws Exception {
        RewriteRule[] rules = config.getRules();
        if (rules == null || rules.length == 0) {
            next.handleRequest(exchange);
            return;
        }

        if (Boolean.TRUE.equals(invoked.get())) {
            next.handleRequest(exchange);
            invoked.set(null);
            return;
        }
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        HttpServletRequestImpl request = src.getOriginalRequest();
        HttpServletResponseImpl response = src.getOriginalResponse();
        UndertowResolver resolver = new UndertowResolver(src, src.getOriginalRequest());

        invoked.set(Boolean.TRUE);

        // As long as MB isn't a char sequence or affiliated, this has to be
        // converted to a string
        CharSequence url = exchange.getRelativePath();
        CharSequence host = request.getServerName();
        boolean rewritten = false;
        boolean done = false;
        for (int i = 0; i < rules.length; i++) {
            CharSequence test = (rules[i].isHost()) ? host : url;
            CharSequence newtest = rules[i].evaluate(test, resolver);
            if (newtest != null && !test.equals(newtest.toString())) {
                if (UndertowServletLogger.REQUEST_LOGGER.isDebugEnabled()) {
                    UndertowServletLogger.REQUEST_LOGGER.debug("Rewrote " + test + " as " + newtest
                            + " with rule pattern " + rules[i].getPatternString());
                }
                if (rules[i].isHost()) {
                    host = newtest;
                } else {
                    url = newtest;
                }
                rewritten = true;
            }

            // Final reply

            // - forbidden
            if (rules[i].isForbidden() && newtest != null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                done = true;
                break;
            }
            // - gone
            if (rules[i].isGone() && newtest != null) {
                response.sendError(HttpServletResponse.SC_GONE);
                done = true;
                break;
            }
            // - redirect (code)
            if (rules[i].isRedirect() && newtest != null) {
                // append the query string to the url if there is one and it hasn't been rewritten
                String queryString = request.getQueryString();
                StringBuffer urlString = new StringBuffer(url);
                if (queryString != null && queryString.length() > 0) {
                    int index = urlString.indexOf("?");
                    if (index != -1) {
                        // if qsa is specified append the query
                        if (rules[i].isQsappend()) {
                            urlString.append('&');
                            urlString.append(queryString);
                        }
                        // if the ? is the last character delete it, its only purpose was to
                        // prevent the rewrite module from appending the query string
                        else if (index == urlString.length() - 1) {
                            urlString.deleteCharAt(index);
                        }
                    } else {
                        urlString.append('?');
                        urlString.append(queryString);
                    }
                }
                // Insert the context if
                // 1. this valve is associated with a context
                // 2. the url starts with a leading slash
                // 3. the url isn't absolute
                if (urlString.charAt(0) == '/' && !hasScheme(urlString)) {
                    urlString.insert(0, request.getContextPath());
                }
                response.sendRedirect(urlString.toString());
                response.setStatus(rules[i].getRedirectCode());
                done = true;
                break;
            }

            // Reply modification

            // - cookie
            if (rules[i].isCookie() && newtest != null) {
                Cookie cookie = new Cookie(rules[i].getCookieName(),
                        rules[i].getCookieResult());
                cookie.setDomain(rules[i].getCookieDomain());
                cookie.setMaxAge(rules[i].getCookieLifetime());
                cookie.setPath(rules[i].getCookiePath());
                cookie.setSecure(rules[i].isCookieSecure());
                cookie.setHttpOnly(rules[i].isCookieHttpOnly());
                response.addCookie(cookie);
            }
            // - env (note: this sets a request attribute)
            if (rules[i].isEnv() && newtest != null) {
                for (int j = 0; j < rules[i].getEnvSize(); j++) {
                    request.setAttribute(rules[i].getEnvName(j), rules[i].getEnvResult(j));
                }
            }
            // - content type (note: this will not force the content type, use a filter
            //   to do that)
            if (rules[i].isType() && newtest != null) {
                exchange.getRequestHeaders().put(Headers.CONTENT_TYPE, rules[i].getTypeValue());
            }
            // - qsappend
            if (rules[i].isQsappend() && newtest != null) {
                String queryString = request.getQueryString();
                String urlString = url.toString();
                if (urlString.indexOf('?') != -1 && queryString != null) {
                    url = urlString + "&" + queryString;
                }
            }

            // Control flow processing

            // - chain (skip remaining chained rules if this one does not match)
            if (rules[i].isChain() && newtest == null) {
                for (int j = i; j < rules.length; j++) {
                    if (!rules[j].isChain()) {
                        i = j;
                        break;
                    }
                }
                continue;
            }
            // - last (stop rewriting here)
            if (rules[i].isLast() && newtest != null) {
                break;
            }
            // - next (redo again)
            if (rules[i].isNext() && newtest != null) {
                i = 0;
                continue;
            }
            // - skip (n rules)
            if (newtest != null) {
                i += rules[i].getSkip();
            }

        }

        if (rewritten) {
            if (!done) {
                // See if we need to replace the query string
                String urlString = url.toString();
                String queryString = null;
                int queryIndex = urlString.indexOf('?');
                if (queryIndex != -1) {
                    queryString = urlString.substring(queryIndex + 1);
                    urlString = urlString.substring(0, queryIndex);
                }
                // Set the new URL
                StringBuilder chunk = new StringBuilder();
                chunk.append(request.getContextPath());
                chunk.append(urlString);
                String requestPath = chunk.toString();
                exchange.setRequestPath(requestPath);
                exchange.setRelativePath(urlString);

                // Set the new Query if there is one
                if (queryString != null) {
                    exchange.setQueryString(queryString);
                    exchange.getQueryParameters().clear();
                    exchange.getQueryParameters().putAll(QueryParameterUtils.parseQueryString(queryString, exchange.getConnection().getUndertowOptions().get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name())));
                }
                // Set the new host if it changed
                if (!host.equals(request.getServerName())) {
                    exchange.getRequestHeaders().put(Headers.HOST, host + ":" + exchange.getHostPort());
                }
                // Reinvoke the whole request recursively
                src.getDeployment().getHandler().handleRequest(exchange);
            }
        } else {
            next.handleRequest(exchange);
        }

        invoked.set(null);

    }


    /**
     * Determine if a URI string has a <code>scheme</code> component.
     */
    protected static boolean hasScheme(StringBuffer uri) {
        int len = uri.length();
        for (int i = 0; i < len; i++) {
            char c = uri.charAt(i);
            if (c == ':') {
                return i > 0;
            } else if (!isSchemeChar(c)) {
                return false;
            }
        }
        return false;
    }

    /**
     * Determine if the character is allowed in the scheme of a URI.
     * See RFC 2396, Section 3.1
     */
    private static boolean isSchemeChar(char c) {
        return Character.isLetterOrDigit(c) ||
                c == '+' || c == '-' || c == '.';
    }

}
