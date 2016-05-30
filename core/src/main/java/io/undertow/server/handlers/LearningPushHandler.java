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

package io.undertow.server.handlers;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.handlers.cache.LRUCache;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.util.DateUtils;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler that builds up a cache of resources that a browsers requests, and uses
 * server push to push them when supported.
 *
 * @author Stuart Douglas
 */
public class LearningPushHandler implements HttpHandler {

    private static final String SESSION_ATTRIBUTE = "io.undertow.PUSHED_RESOURCES";
    private static final int DEFAULT_MAX_CACHE_ENTRIES = 1000;
    private static final int DEFAULT_MAX_CACHE_AGE = -1;

    private final LRUCache<String, Map<String, PushedRequest>> cache;

    private final HttpHandler next;

    public LearningPushHandler(final HttpHandler next) {
        this(DEFAULT_MAX_CACHE_ENTRIES, DEFAULT_MAX_CACHE_AGE, next);
    }

    public LearningPushHandler(int maxEntries, int maxAge, HttpHandler next) {
        this.next = next;
        cache = new LRUCache<>(maxEntries, maxAge);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String fullPath;
        String requestPath;
        if(exchange.getQueryString().isEmpty()) {
            fullPath = exchange.getRequestURL();
            requestPath = exchange.getRequestPath();
        } else{
            fullPath = exchange.getRequestURL() + "?" + exchange.getQueryString();
            requestPath = exchange.getRequestPath() + "?" + exchange.getQueryString();
        }

        doPush(exchange, fullPath);
        String referrer = exchange.getRequestHeaders().getFirst(Headers.REFERER);
        if (referrer != null) {
            String accept = exchange.getRequestHeaders().getFirst(Headers.ACCEPT);
            if (accept == null || !accept.contains("text/html")) {
                //if accept contains text/html it generally means the user has clicked
                //a link to move to a new page, and is not a resource load for the current page
                //we only care about resources for the current page

                exchange.addExchangeCompleteListener(new PushCompletionListener(fullPath, requestPath, referrer));
            }
        }
        next.handleRequest(exchange);
    }

    private void doPush(HttpServerExchange exchange, String fullPath) {
        if (exchange.getConnection().isPushSupported()) {
            Map<String, PushedRequest> toPush = cache.get(fullPath);
            if (toPush != null) {
                Session session = getSession(exchange);
                if (session == null) {
                    return;
                }
                Map<String, Object> pushed = (Map<String, Object>) session.getAttribute(SESSION_ATTRIBUTE);
                if (pushed == null) {
                    pushed = Collections.synchronizedMap(new HashMap<String, Object>());
                }
                for (Map.Entry<String, PushedRequest> entry : toPush.entrySet()) {
                    PushedRequest request = entry.getValue();
                    Object pushedKey = pushed.get(request.getPath());
                    boolean doPush = pushedKey == null;
                    if (!doPush) {
                        if (pushedKey instanceof String && !pushedKey.equals(request.getEtag())) {
                            doPush = true;
                        } else if (pushedKey instanceof Long && ((Long) pushedKey) != request.getLastModified()) {
                            doPush = true;
                        }
                    }
                    if (doPush) {
                        exchange.getConnection().pushResource(request.getPath(), Methods.GET, request.getRequestHeaders());
                        if(request.getEtag() != null) {
                            pushed.put(request.getPath(), request.getEtag());
                        } else {
                            pushed.put(request.getPath(), request.getLastModified());
                        }
                    }
                }
                session.setAttribute(SESSION_ATTRIBUTE, pushed);
            }

        }
    }

    protected Session getSession(HttpServerExchange exchange) {
        SessionConfig sc = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);
        SessionManager sm = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        if (sc == null || sm == null) {
            return null;
        }
        Session session = sm.getSession(exchange, sc);
        if (session == null) {
            return sm.createSession(exchange, sc);
        }
        return session;
    }

    private final class PushCompletionListener implements ExchangeCompletionListener {

        private final String fullPath;
        private final String requestPath;
        private final String referer;

        private PushCompletionListener(String fullPath, String requestPath, String referer) {
            this.fullPath = fullPath;
            this.requestPath = requestPath;
            this.referer = referer;
        }

        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            if (exchange.getStatusCode() == 200 && referer != null) {
                //for now only cache 200 response codes
                String lmString = exchange.getResponseHeaders().getFirst(Headers.LAST_MODIFIED);
                String etag = exchange.getResponseHeaders().getFirst(Headers.ETAG);
                long lastModified = -1;
                if(lmString != null) {
                    Date dt = DateUtils.parseDate(lmString);
                    if(dt != null) {
                        lastModified = dt.getTime();
                    }
                }
                Map<String, PushedRequest> pushes = cache.get(referer);
                if(pushes == null) {
                    synchronized (cache) {
                        pushes = cache.get(referer);
                        if(pushes == null) {
                            cache.add(referer, pushes = Collections.synchronizedMap(new HashMap<String, PushedRequest>()));
                        }
                    }
                }
                pushes.put(fullPath, new PushedRequest(new HeaderMap(), requestPath, etag, lastModified));
            }

            nextListener.proceed();
        }
    }

    private static class PushedRequest {
        private final HeaderMap requestHeaders;
        private final String path;
        private final String etag;
        private final long lastModified;

        private PushedRequest(HeaderMap requestHeaders, String path, String etag, long lastModified) {
            this.requestHeaders = requestHeaders;
            this.path = path;
            this.etag = etag;
            this.lastModified = lastModified;
        }

        public HeaderMap getRequestHeaders() {
            return requestHeaders;
        }

        public String getPath() {
            return path;
        }

        public String getEtag() {
            return etag;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "learning-push";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("max-age", Integer.class);
            params.put("max-entries", Integer.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return null;
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            final int maxAge = config.containsKey("max-age") ? (Integer)config.get("max-age") : DEFAULT_MAX_CACHE_AGE;
            final int maxEntries = config.containsKey("max-entries") ? (Integer)config.get("max-entries") : DEFAULT_MAX_CACHE_ENTRIES;

            return new HandlerWrapper() {
                @Override
                public HttpHandler wrap(HttpHandler handler) {
                    return new LearningPushHandler(maxEntries, maxAge, handler);
                }
            };
        }
    }
}
