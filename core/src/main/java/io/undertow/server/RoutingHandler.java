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

    private final Map<HttpString, PathTemplateMatcher<RoutingMatch>> matches = new CopyOnWriteMap<>();
    private final PathTemplateMatcher<RoutingMatch> allMethodsMatcher = new PathTemplateMatcher<>();

    private volatile HttpHandler fallbackHandler = ResponseCodeHandler.HANDLE_404;
    private volatile HttpHandler invalidMethodHandler = ResponseCodeHandler.HANDLE_405;

    /**
     * If this is true then path matches will be added to the query parameters for easy access by
     * later handlers.
     */
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
            invalidMethodHandler.handleRequest(exchange);
            return;
        }
        PathTemplateMatcher.PathMatchResult<RoutingMatch> match = matcher.match(exchange.getRelativePath());
        if (match == null) {
            if (allMethodsMatcher.match(exchange.getRelativePath()) != null) {
                invalidMethodHandler.handleRequest(exchange);
                return;
            }
            fallbackHandler.handleRequest(exchange);
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
        if (allMethodsMatcher.get(template) == null) {
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
        if (allMethodsMatcher.get(template) == null) {
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
                if (allMethodsMatcher.get(template.getTemplateString()) == null) {
                    allMethodsMatcher.add(template, new RoutingMatch());
                }
            }
        }
        return this;
    }

    Map<HttpString, PathTemplateMatcher<RoutingMatch>> getMatches() {
        return matches;
    }

    public HttpHandler getFallbackHandler() {
        return fallbackHandler;
    }

    public RoutingHandler setFallbackHandler(HttpHandler fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
        return this;
    }

    public HttpHandler getInvalidMethodHandler() {
        return invalidMethodHandler;
    }

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
