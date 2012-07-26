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

package tmp.texugo.server.handlers;

import tmp.texugo.server.HttpCompletionHandler;
import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;
import tmp.texugo.util.CopyOnWriteMap;

import java.util.concurrent.ConcurrentMap;

/**
 * Handler that dispatches to a given handler based of a prefix match of the path.
 * <p/>
 * This only matches a single level of a request, e.g if you have a request that takes the form:
 * <p/>
 * /foo/bar
 * <p/>
 * This handler can only match a single part of this request (namely /foo). To match the full path
 * two of these handlers must be chained together.
 *
 * @author Stuart Douglas
 */
public class PathHandler implements HttpHandler {

    private volatile HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_404;
    private final ConcurrentMap<String, HttpHandler> paths = new CopyOnWriteMap<String, HttpHandler>();

    @Override
    public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        int pos = 0;
        final String path = exchange.getRelativePath();
        final int length = path.length();

        while (pos < length) {
            if(path.charAt(pos) == '/' && pos != 0) {
                break;
            }
            ++pos;
        }
        final String part = path.substring(0, pos);
        final HttpHandler next = paths.get(part);
        if(next != null) {
            exchange.setRelativePath(path.substring(pos));
            next.handleRequest(exchange, completionHandler);
        } else {
            defaultHandler.handleRequest(exchange, completionHandler);
        }
    }

    public HttpHandler getDefaultHandler() {
        return defaultHandler;
    }

    public void setDefaultHandler(HttpHandler defaultHandler) {
        this.defaultHandler = defaultHandler;
    }

    public ConcurrentMap<String, HttpHandler> getPaths() {
        return paths;
    }
}
