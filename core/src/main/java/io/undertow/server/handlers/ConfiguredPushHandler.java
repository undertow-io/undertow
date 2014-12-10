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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.PathMatcher;

/**
 * Handler that pushes resources based on a provided URL
 *
 * @author Stuart Douglas
 */
public class ConfiguredPushHandler implements HttpHandler {

    private final PathMatcher<String[]> pathMatcher = new PathMatcher<>();
    private final HttpHandler next;
    private final HeaderMap requestHeaders = new HeaderMap();

    public ConfiguredPushHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if(exchange.getConnection().isPushSupported()) {
            PathMatcher.PathMatch<String[]> result = pathMatcher.match(exchange.getRelativePath());
            if(result != null) {
                String[] value = result.getValue();
                for(int i = 0; i < value.length; ++i) {
                    exchange.getConnection().pushResource(value[i], Methods.GET, requestHeaders);
                }
            }
        }
        next.handleRequest(exchange);
    }

    public ConfiguredPushHandler addRequestHeader(HttpString name, String value) {
        requestHeaders.put(name, value);
        return this;
    }

    public ConfiguredPushHandler addRoute(String url, String ... resourcesToPush) {
        if(url.endsWith("/*")) {
            String partial = url.substring(0, url.length() - 1);
            pathMatcher.addPrefixPath(partial, resourcesToPush);
        } else {
            pathMatcher.addExactPath(url, resourcesToPush);
        }
        return this;
    }

}
