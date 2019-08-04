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

package io.undertow.server;

import io.undertow.predicate.Predicate;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.PathTemplate;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.PathTemplateMatcher;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Handler that handles the common case of routing via path template and method name.
 *
 * @author Stuart Douglas
 */
public class RoutingHandler implements HttpHandler {

    // Matcher objects grouped by http methods.
    private final Map<HttpString, PathTemplateMatcher<RoutingMatch>> matches = new CopyOnWriteMap<>();
    // Matcher used to find if this instance contains matches for any http method for a path.
    // This matcher is used to report if this instance can match a path for one of the http methods.
    private final PathTemplateMatcher<RoutingMatch> allMethodsMatcher = new PathTemplateMatcher<>();

    // Handler called when no match was found and invalid method handler can't be invoked.
    private volatile HttpHandler fallbackHandler = ResponseCodeHandler.HANDLE_404;
    // Handler called when this instance can not match the http method but can match another http method.
    // For example: For an exchange the POST method is not matched by this instance but at least one http method is
    // matched for the same exchange.
    // If this handler is null the fallbackHandler will be used.
    private volatile HttpHandler invalidMethodHandler = ResponseCodeHandler.HANDLE_405;

    // If this is true then path matches will be added to the query parameters for easy access by later handlers.
    private final boolean rewriteQueryParameters;

    public RoutingHandler(boolean rewriteQueryParameters) {
        this.rewriteQueryParameters = rewriteQueryParameters;
    }

    public RoutingHandler() {
        this.rewriteQueryParameters = true;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        PathTemplateMatcher<RoutingMatch> matcher = matches.get(exchange.getRequestMethod());
        if (matcher == null) {
            handleNoMatch(exchange);
            return;
        }
        PathTemplateMatcher.PathMatchResult<RoutingMatch> match = matcher.match(exchange.getRelativePath());
        if (match == null) {
            handleNoMatch(exchange);
            return;
        }
        exchange.putAttachment(PathTemplateMatch.ATTACHMENT_KEY, match);
        if (rewriteQueryParameters) {
            for (Map.Entry<String, String> entry : match.getParameters().entrySet()) {
                exchange.addQueryParam(entry.getKey(), entry.getValue());
            }
        }
        for (HandlerHolder handler : match.getValue().predicatedHandlers) {
            if (handler.predicate.resolve(exchange)) {
                handler.handler.handleRequest(exchange);
                return;
            }
        }
        if (match.getValue().defaultHandler != null) {
            match.getValue().defaultHandler.handleRequest(exchange);
        } else {
            fallbackHandler.handleRequest(exchange);
        }
    }

    /**
     * Handles the case in with a match was not found for the http method but might exist for another http method.
     * For example: POST not matched for a path but at least one match exists for same path.
     *
     * @param exchange The object for which its handled the "no match" case.
     * @throws Exception
     */
    private void handleNoMatch(final HttpServerExchange exchange) throws Exception {
        // if invalidMethodHandler is null we fail fast without matching with allMethodsMatcher
        if (invalidMethodHandler != null && allMethodsMatcher.match(exchange.getRelativePath()) != null) {
            invalidMethodHandler.handleRequest(exchange);
            return;
        }
        fallbackHandler.handleRequest(exchange);
    }

    public synchronized RoutingHandler add(final String method, final String template, HttpHandler handler) {
        return add(new HttpString(method), template, handler);
    }

    public synchronized RoutingHandler add(HttpString method, String template, HttpHandler handler) {
        PathTemplateMatcher<RoutingMatch> matcher = matches.get(method);
        if (matcher == null) {
            matches.put(method, matcher = new PathTemplateMatcher<>());
        }
        RoutingMatch res = matcher.get(template);
        if (res == null) {
            matcher.add(template, res = new RoutingMatch());
        }
        if (allMethodsMatcher.match(template) == null) {
            allMethodsMatcher.add(template, res);
        }
        res.defaultHandler = handler;
        return this;
    }

    public synchronized RoutingHandler get(final String template, HttpHandler handler) {
        return add(Methods.GET, template, handler);
    }

    public synchronized RoutingHandler post(final String template, HttpHandler handler) {
        return add(Methods.POST, template, handler);
    }

    public synchronized RoutingHandler put(final String template, HttpHandler handler) {
        return add(Methods.PUT, template, handler);
    }

    public synchronized RoutingHandler delete(final String template, HttpHandler handler) {
        return add(Methods.DELETE, template, handler);
    }

    public synchronized RoutingHandler add(final String method, final String template, Predicate predicate, HttpHandler handler) {
        return add(new HttpString(method), template, predicate, handler);
    }

    public synchronized RoutingHandler add(HttpString method, String template, Predicate predicate, HttpHandler handler) {
        PathTemplateMatcher<RoutingMatch> matcher = matches.get(method);
        if (matcher == null) {
            matches.put(method, matcher = new PathTemplateMatcher<>());
        }
        RoutingMatch res = matcher.get(template);
        if (res == null) {
            matcher.add(template, res = new RoutingMatch());
        }
        if (allMethodsMatcher.match(template) == null) {
            allMethodsMatcher.add(template, res);
        }
        res.predicatedHandlers.add(new HandlerHolder(predicate, handler));
        return this;
    }

    public synchronized RoutingHandler get(final String template, Predicate predicate, HttpHandler handler) {
        return add(Methods.GET, template, predicate, handler);
    }

    public synchronized RoutingHandler post(final String template, Predicate predicate, HttpHandler handler) {
        return add(Methods.POST, template, predicate, handler);
    }

    public synchronized RoutingHandler put(final String template, Predicate predicate, HttpHandler handler) {
        return add(Methods.PUT, template, predicate, handler);
    }

    public synchronized RoutingHandler delete(final String template, Predicate predicate, HttpHandler handler) {
        return add(Methods.DELETE, template, predicate, handler);
    }

    public synchronized RoutingHandler addAll(RoutingHandler routingHandler) {
        for (Entry<HttpString, PathTemplateMatcher<RoutingMatch>> entry : routingHandler.getMatches().entrySet()) {
            HttpString method = entry.getKey();
            PathTemplateMatcher<RoutingMatch> matcher = matches.get(method);
            if (matcher == null) {
                matches.put(method, matcher = new PathTemplateMatcher<>());
            }
            matcher.addAll(entry.getValue());
            // If we use allMethodsMatcher.addAll() we can have duplicate
            // PathTemplates which we want to ignore here so it does not crash.
            for (PathTemplate template : entry.getValue().getPathTemplates()) {
                if (allMethodsMatcher.match(template.getTemplateString()) == null) {
                    allMethodsMatcher.add(template, new RoutingMatch());
                }
            }
        }
        return this;
    }

    /**
     *
     * Removes the specified route from the handler
     *
     * @param method The method to remove
     * @param path the path tempate to remove
     * @return this handler
     */
    public RoutingHandler remove(HttpString method, String path) {
        PathTemplateMatcher<RoutingMatch> handler = matches.get(method);
        if(handler != null) {
            handler.remove(path);
        }
        return this;
    }


    /**
     *
     * Removes the specified route from the handler
     *
     * @param path the path tempate to remove
     * @return this handler
     */
    public RoutingHandler remove(String path) {
        allMethodsMatcher.remove(path);
        return this;
    }

    Map<HttpString, PathTemplateMatcher<RoutingMatch>> getMatches() {
        return matches;
    }

    /**
     * @return Handler called when no match was found and invalid method handler can't be invoked.
     */
    public HttpHandler getFallbackHandler() {
        return fallbackHandler;
    }

    /**
     * @param fallbackHandler Handler that will be called when no match was found and invalid method handler can't be
     * invoked.
     * @return This instance.
     */
    public RoutingHandler setFallbackHandler(HttpHandler fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
        return this;
    }

    /**
     * @return Handler called when this instance can not match the http method but can match another http method.
     */
    public HttpHandler getInvalidMethodHandler() {
        return invalidMethodHandler;
    }

    /**
     * Sets the handler called when this instance can not match the http method but can match another http method.
     * For example: For an exchange the POST method is not matched by this instance but at least one http method matched
     * for the exchange.
     * If this handler is null the fallbackHandler will be used.
     *
     * @param invalidMethodHandler Handler that will be called when this instance can not match the http method but can
     * match another http method.
     * @return This instance.
     */
    public RoutingHandler setInvalidMethodHandler(HttpHandler invalidMethodHandler) {
        this.invalidMethodHandler = invalidMethodHandler;
        return this;
    }

    private static class RoutingMatch {

        final List<HandlerHolder> predicatedHandlers = new CopyOnWriteArrayList<>();
        volatile HttpHandler defaultHandler;

    }

    private static class HandlerHolder {
        final Predicate predicate;
        final HttpHandler handler;

        private HandlerHolder(Predicate predicate, HttpHandler handler) {
            this.predicate = predicate;
            this.handler = handler;
        }
    }

}
