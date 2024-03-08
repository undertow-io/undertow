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
import io.undertow.util.AttachmentKey;
import io.undertow.util.PathTemplateRouter;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A drop-in substitute for the old PathTemplateHandler class. Ideally, one should use {@link PathTemplateRouterHandler}
 * by instantiating it with a {@link PathTemplateRouter}. This class implements all of the methods from
 * the original PathTemplateHandler to provide backwards compatibility.
 *
 * @author Dirk Roets dirkroets@gmail.com. This class was originally written by Stuart Douglas. After the introduction
 * of {@link PathTemplateRouter}, it was rewritten against the original interface and tests.
 * @since 2023-07-20
 */
public class PathTemplateHandler implements HttpHandler {

    /**
     * @see io.undertow.util.PathTemplateMatch#ATTACHMENT_KEY
     */
    @Deprecated
    public static final AttachmentKey<PathTemplateHandler.PathTemplateMatch> PATH_TEMPLATE_MATCH = AttachmentKey.
            create(PathTemplateHandler.PathTemplateMatch.class);

    private final boolean rewriteQueryParameters;
    private final PathTemplateRouter.SimpleBuilder<HttpHandler> builder;
    private final Object lock = new Object();
    private volatile PathTemplateRouter.Router<HttpHandler> router;

    /**
     * Default constructor. Uses {@link ResponseCodeHandler#HANDLE_404} as the next (default) handler and sets
     * 'rewriteQueryParameters' to 'true'.
     */
    public PathTemplateHandler() {
        this(true);
    }

    /**
     * Uses {@link ResponseCodeHandler#HANDLE_404} as the next (default) handler.
     *
     * @param rewriteQueryParameters Path parameters that are returned by the underlying router will be added as
     *                               query parameters to the exchange if this flag is 'true'.
     */
    public PathTemplateHandler(final boolean rewriteQueryParameters) {
        this(ResponseCodeHandler.HANDLE_404, rewriteQueryParameters);
    }

    /**
     * Sets 'rewriteQueryParameters' to 'true'.
     *
     * @param next The next (default) handler to use when requests do not match any of the specified
     *             templates.
     */
    public PathTemplateHandler(final HttpHandler next) {
        this(next, true);
    }

    /**
     * @param next                   The next (default) handler to use when requests do not match any of the specified
     *                               templates.
     * @param rewriteQueryParameters Path parameters that are returned by the underlying router will be added as
     *                               query parameters to the exchange if this flag is 'true'.
     */
    public PathTemplateHandler(final HttpHandler next, final boolean rewriteQueryParameters) {
        Objects.requireNonNull(next);

        this.rewriteQueryParameters = rewriteQueryParameters;
        builder = PathTemplateRouter.SimpleBuilder.newBuilder(next);
        router = builder.build();
    }

    /**
     * Adds a template and handler to the underlying router.
     *
     * @param uriTemplate The URI path template.
     * @param handler     The handler to use for requests that match the specified template.
     *
     * @return Reference to this handler.
     */
    public PathTemplateHandler add(final String uriTemplate, final HttpHandler handler) {
        Objects.requireNonNull(uriTemplate);
        Objects.requireNonNull(handler);

        // Router builders are not thread-safe, so we need to synchronize.
        synchronized (lock) {
            builder.addTemplate(uriTemplate, handler);
            router = builder.build();
        }

        return this;
    }

    /**
     * Removes a template from the underlying router.
     *
     * @param uriTemplate The URI path template.
     *
     * @return Reference to this handler.
     */
    public PathTemplateHandler remove(final String uriTemplate) {
        Objects.requireNonNull(uriTemplate);

        // Router builders are not thread-safe, so we need to synchronize.
        synchronized (lock) {
            builder.removeTemplate(uriTemplate);
            router = builder.build();
        }

        return this;
    }

    @Override
    public String toString() {
        final List<PathTemplateRouter.PatternEqualsAdapter<PathTemplateRouter.Template<Supplier<HttpHandler>>>> templates;
        synchronized (lock) {
            templates = new ArrayList<>(builder.getBuilder().getTemplates().keySet());
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("path-template( ");
        if (templates.size() == 1) {
            sb.append(templates.get(0).getElement().getPathTemplate()).append(" )");
        } else {
            sb.append('{').append(
                    templates.stream().map(s -> s.getElement().getPathTemplate()).collect(Collectors.joining(", "))
            ).append("} )");
        }
        return sb.toString();
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final PathTemplateRouter.RouteResult<HttpHandler> routeResult = router.apply(exchange.getRelativePath());
        if (routeResult.getPathTemplate().isEmpty()) {
            // This is the default handler, therefore it doesn't contain path parameters.
            routeResult.getTarget().handleRequest(exchange);
            return;
        }

        exchange.putAttachment(PATH_TEMPLATE_MATCH, new PathTemplateMatch(routeResult));
        exchange.putAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY, routeResult);
        if (rewriteQueryParameters) {
            for (Map.Entry<String, String> entry : routeResult.getParameters().entrySet()) {
                exchange.addQueryParam(entry.getKey(), entry.getValue());
            }
        }
        routeResult.getTarget().handleRequest(exchange);
    }

    /**
     * @see io.undertow.util.PathTemplateMatch
     */
    @Deprecated
    public static final class PathTemplateMatch {

        private final io.undertow.util.PathTemplateMatch pathTemplateMatch;

        PathTemplateMatch(
                final io.undertow.util.PathTemplateMatch pathTemplateMatch
        ) {
            this.pathTemplateMatch = Objects.requireNonNull(pathTemplateMatch);
        }

        public PathTemplateMatch(final String matchedTemplate, final Map<String, String> parameters) {
            this(
                    new io.undertow.util.PathTemplateMatch(matchedTemplate, parameters)
            );
        }

        public String getMatchedTemplate() {
            return pathTemplateMatch.getMatchedTemplate();
        }

        public Map<String, String> getParameters() {
            return pathTemplateMatch.getParameters();
        }
    }
}
