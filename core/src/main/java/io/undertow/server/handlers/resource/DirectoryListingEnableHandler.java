/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package io.undertow.server.handlers.resource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.AttachmentKey;

/**
 * @author baranowb
 * Handler which enables/disabled per exchange listing.
 */
public class DirectoryListingEnableHandler implements HttpHandler {

    private static final AttachmentKey<Boolean> ENABLE_DIRECTORY_LISTING = AttachmentKey.create(Boolean.class);
    /**
     * Handler that is called if no resource is found
     */
    private final HttpHandler next;
    private final boolean allowsListing;

    public DirectoryListingEnableHandler(HttpHandler next, boolean allowsListing) {
        super();
        this.next = next;
        this.allowsListing = allowsListing;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.putAttachment(ENABLE_DIRECTORY_LISTING, this.allowsListing);
        if (this.next != null) {
            this.next.handleRequest(exchange);
        }
    }

    public static boolean hasEnablerAttached(final HttpServerExchange exchange) {
        return exchange.getAttachment(ENABLE_DIRECTORY_LISTING) != null;
    }

    public static boolean isDirectoryListingEnabled(final HttpServerExchange exchange) {
        return exchange.getAttachment(ENABLE_DIRECTORY_LISTING);
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "directory-listing";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("allow-listing", boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("allow-listing");
        }

        @Override
        public String defaultParameter() {
            return "allow-listing";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper((Boolean) config.get("allow-listing"));
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final boolean allowDirectoryListing;

        private Wrapper(boolean allowDirectoryListing) {
            this.allowDirectoryListing = allowDirectoryListing;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            final DirectoryListingEnableHandler enableHandler = new DirectoryListingEnableHandler(handler,
                    allowDirectoryListing);
            return enableHandler;
        }
    }

}
