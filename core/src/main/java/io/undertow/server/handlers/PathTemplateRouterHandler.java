/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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
import io.undertow.util.PathTemplateRouter;
import io.undertow.util.PathTemplateRouteResult;
import io.undertow.util.PathTemplateRouterFactory;
import java.util.Map;
import java.util.Objects;

import static io.undertow.server.handlers.PathTemplateHandler.PATH_TEMPLATE_MATCH;

/**
 * A handler that matches URI templates.
 *
 * @author Dirk Roets
 * @see PathTemplateRouterFactory
 */
public class PathTemplateRouterHandler implements HttpHandler {

    private final PathTemplateRouter<HttpHandler> router;
    private final boolean rewriteQueryParameters;

    /**
     * @param router The path template router to use.
     * @param rewriteQueryParameters Path parameters that are returned by the specified router will be added as query parameters
     * to the exchange if this flag is 'true'.
     */
    public PathTemplateRouterHandler(
            final PathTemplateRouter<HttpHandler> router,
            final boolean rewriteQueryParameters
    ) {
        this.router = Objects.requireNonNull(router);
        this.rewriteQueryParameters = rewriteQueryParameters;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final PathTemplateRouteResult<HttpHandler> routeResult = router.route(exchange.getRelativePath());
        if (routeResult.getPathTemplate().isEmpty()) {
            // This is the default handler, therefore it doesn't contain path parameters.
            routeResult.getTarget().handleRequest(exchange);
            return;
        }

        exchange.putAttachment(PATH_TEMPLATE_MATCH, new PathTemplateHandler.PathTemplateMatch(routeResult));
        exchange.putAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY, routeResult);
        if (rewriteQueryParameters && !routeResult.getParameters().isEmpty()) {
            for (final Map.Entry<String, String> entry : routeResult.getParameters().entrySet()) {
                exchange.addQueryParam(entry.getKey(), entry.getValue());
            }
        }
        routeResult.getTarget().handleRequest(exchange);
    }
}
