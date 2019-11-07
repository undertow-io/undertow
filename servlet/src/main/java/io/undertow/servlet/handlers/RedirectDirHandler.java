/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.RedirectBuilder;
import io.undertow.util.StatusCodes;

/**
 * Handler that redirects the directory requests without trailing slash to the one append trailing slash.
 *
 * @author Lin Gao
 */
public class RedirectDirHandler implements HttpHandler {

    private static final String HTTP2_UPGRADE_PREFIX = "h2";

    private final HttpHandler next;
    private final ServletPathMatches paths;

    public RedirectDirHandler(HttpHandler next, ServletPathMatches paths) {
        this.next = next;
        this.paths = paths;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final String path = exchange.getRelativePath();
        final ServletPathMatch info = paths.getServletHandlerByPath(path);
        // https://issues.jboss.org/browse/WFLY-3439
        // if the request is an upgrade request then we don't want to redirect
        // as there is a good chance the web socket client won't understand the redirect
        // we make an exception for HTTP2 upgrade requests, as this would have already be handled at
        // the connector level if it was going to be handled.
        String upgradeString = exchange.getRequestHeaders().getFirst(Headers.UPGRADE);
        boolean isUpgradeRequest = upgradeString != null && !upgradeString.startsWith(HTTP2_UPGRADE_PREFIX);
        if (info.getType() == ServletPathMatch.Type.REDIRECT && !isUpgradeRequest) {
            // UNDERTOW-89
            // we redirect on GET requests to the root context to add an / to the end
            if (exchange.getRequestMethod().equals(Methods.GET) || exchange.getRequestMethod().equals(Methods.HEAD)) {
                exchange.setStatusCode(StatusCodes.FOUND);
            } else {
                exchange.setStatusCode(StatusCodes.TEMPORARY_REDIRECT);
            }
            exchange.getResponseHeaders().put(Headers.LOCATION,
                    RedirectBuilder.redirect(exchange, exchange.getRelativePath() + "/", true));
            return;
        }
        next.handleRequest(exchange);
    }

}
