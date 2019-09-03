/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package io.undertow.server.handlers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResponseCommitListener;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.Headers;
import io.undertow.util.SameSiteNoneIncompatibleClientChecker;

/**
 * Handler that will set the SameSite flag to response cookies
 */
public class SameSiteCookieHandler implements HttpHandler {

    private final HttpHandler next;
    private final String mode;
    private final Pattern cookiePattern;
    private final boolean enableClientChecker;

    public SameSiteCookieHandler(final HttpHandler next, final String mode) {
        this(next, mode, null, true, true);
    }

    public SameSiteCookieHandler(final HttpHandler next, final String mode, final boolean enableClientChecker) {
        this(next, mode, null, true, enableClientChecker);
    }

    public SameSiteCookieHandler(final HttpHandler next, final String mode, final String cookiePattern) {
        this(next, mode, cookiePattern, true, true);
    }

    public SameSiteCookieHandler(final HttpHandler next, final String mode, final String cookiePattern, final boolean caseSensitive) {
        this(next, mode, cookiePattern, caseSensitive, true);
    }

    public SameSiteCookieHandler(final HttpHandler next, final String mode, final String cookiePattern, final boolean caseSensitive, final boolean enableClientChecker) {
        this.next = next;
        this.mode = mode;
        if (cookiePattern != null && !cookiePattern.isEmpty()) {
            this.cookiePattern = Pattern.compile(cookiePattern, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        } else {
            this.cookiePattern = null;
        }
        this.enableClientChecker = enableClientChecker && CookieSameSiteMode.NONE.toString().equalsIgnoreCase(mode); // client checker is enabled only for "SameSite=None"
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (mode != null) {
            exchange.addResponseCommitListener(new ResponseCommitListener() {
                @Override
                public void beforeCommit(HttpServerExchange exchange) {
                    // Check user-agents and skip sending "SameSite=None" for incompatible user-agents
                    if (enableClientChecker && !SameSiteNoneIncompatibleClientChecker.shouldSendSameSiteNone(exchange.getRequestHeaders().getFirst(Headers.USER_AGENT))) {
                        return;
                    }
                    for (Map.Entry<String, Cookie> cookie : exchange.getResponseCookies().entrySet()) {
                        if (cookiePattern == null || cookiePattern.matcher(cookie.getValue().getName()).matches()) {
                            // set SameSite attribute to all response cookies when cookie pattern is not specified.
                            // or, set SameSite attribute if cookie name matches the specified cookie pattern.
                            cookie.getValue().setSameSiteMode(mode);
                        }
                    }
                }
            });
        }
        next.handleRequest(exchange);
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "samesite-cookie";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> parameters = new HashMap<>();
            parameters.put("mode", String.class);
            parameters.put("cookie-pattern", String.class);
            parameters.put("case-sensitive", Boolean.class);
            parameters.put("enable-client-checker", Boolean.class);
            return parameters;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("mode");
        }

        @Override
        public String defaultParameter() {
            return "mode";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            final String mode = (String) config.get("mode");
            final String pattern = (String) config.get("cookie-pattern");
            final Boolean caseSensitive = (Boolean) config.get("case-sensitive");
            final Boolean enableClientChecker = (Boolean) config.get("enable-client-checker");
            return new HandlerWrapper() {
                @Override
                public HttpHandler wrap(HttpHandler handler) {
                    return new SameSiteCookieHandler(handler, mode, pattern, caseSensitive == null ? true : caseSensitive, enableClientChecker == null ? true : enableClientChecker);
                }
            };
        }
    }
}
