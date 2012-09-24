/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.handlers;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;

/**
 * Handler that resolves servlet paths
 *
 * @author Stuart Douglas
 */
public class ServletMatchingHandler implements HttpHandler {

    private volatile ServletPathMatches paths;

    public ServletMatchingHandler(final ServletPathMatches paths) {
        this.paths = paths;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final String path = exchange.getRelativePath();
        HttpHandlers.executeHandler(paths.getServletHandlerByPath(path), exchange, completionHandler);
    }

    public ServletPathMatches getPaths() {
        return paths;
    }

    public void setPaths(final ServletPathMatches paths) {
        this.paths = paths;
    }

}
