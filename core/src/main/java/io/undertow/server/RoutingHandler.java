/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.PathTemplateRouter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A Handler that handles the common case of routing via path template and method name.
 *
 * @author Dirk Roets. This class was originally written by Stuart Douglas. After the introduction of
 * {@link PathTemplateRouter}, it was rewritten against the original interface and tests.
 */
public class RoutingHandler implements HttpHandler {

    //<editor-fold defaultstate="collapsed" desc="HandlerHolder inner class">
    private static class HandlerHolder {

        private final Predicate predicate;
        private final HttpHandler handler;

        private HandlerHolder(
                final Predicate predicate,
                final HttpHandler handler
        ) {
            this.predicate = Objects.requireNonNull(predicate);
            this.handler = Objects.requireNonNull(handler);
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="RoutingMatch inner class">
    private static class RoutingMatch {

        private final List<HandlerHolder> predicateHandlers;
        private final HttpHandler defaultHandler;

        private RoutingMatch(
                final List<HandlerHolder> predicateHandlers,
                final HttpHandler defaultHandler
        ) {
            this.predicateHandlers = List.copyOf(predicateHandlers);
            this.defaultHandler = defaultHandler; // Allowed to be NULL for backwards compatibility with original implementation.
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="RoutingMatchBuilder inner class">
    private static class RoutingMatchBuilder implements Supplier<RoutingMatch> {

        private final List<HandlerHolder> predicateHandlers = new LinkedList<>();
        private HttpHandler defaultHandler;

        private RoutingMatchBuilder deepCopy() {
            final RoutingMatchBuilder result = new RoutingMatchBuilder();
            result.predicateHandlers.addAll(predicateHandlers);
            result.defaultHandler = defaultHandler;
            return result;
        }

        @Override
        public RoutingMatch get() {
            return new RoutingMatch(predicateHandlers, defaultHandler);
        }
    }

    //</editor-fold>
    //
    //<editor-fold defaultstate="collapsed" desc="Routers inner class">
    private static class Routers {

        private final Map<HttpString, PathTemplateRouter.Router<RoutingMatch>> methodRouters;
        private final PathTemplateRouter.Router<Object> allMethodsRouter;

        private Routers(
                final Map<HttpString, PathTemplateRouter.Router<RoutingMatch>> methodRouters,
                final PathTemplateRouter.Router<Object> allMethodsRouter
        ) {
            this.methodRouters = Objects.requireNonNull(methodRouters);
            this.allMethodsRouter = Objects.requireNonNull(allMethodsRouter);
        }
    }
    //</editor-fold>
    //
    // Builders for path templates.
    private final RoutingMatch noRoutingMatch = new RoutingMatch(List.of(), null);
    private final RoutingMatchBuilder noRoutingMatchBuilder = new RoutingMatchBuilder() {
        @Override
        public RoutingMatch get() {
            return noRoutingMatch;
        }
    };
    private final Map<HttpString, PathTemplateRouter.Builder<RoutingMatchBuilder, RoutingMatch>> methodRouterBuilders
            = new HashMap<>();

    // The routers to use.
    private volatile Routers routers;

    // Handler called when no match was found and invalid method handler can't be invoked.
    private volatile HttpHandler fallbackHandler = ResponseCodeHandler.HANDLE_404;
    // Handler called when this instance can not match the http method but can match another http method.
    // For example: For an exchange the POST method is not matched by this instance but at least one http method is
    // matched for the same exchange.
    // If this handler is null the fallbackHandler will be used.
    private volatile HttpHandler invalidMethodHandler = ResponseCodeHandler.HANDLE_405;

    // If this is true then path matches will be added to the query parameters for easy access by later handlers.
    private final boolean rewriteQueryParameters;

    public RoutingHandler(final boolean rewriteQueryParameters) {
        this.rewriteQueryParameters = rewriteQueryParameters;
        this.routers = new Routers(
                Map.of(),
                PathTemplateRouter.SimpleBuilder.newBuilder(new Object()).build()
        );
    }

    public RoutingHandler() {
        this(true);
    }

    private void handleFallback(final HttpServerExchange exchange) throws Exception {
        final HttpHandler localFallbackHandler = this.fallbackHandler;
        if (localFallbackHandler != null) {
            localFallbackHandler.handleRequest(exchange);
        } else {
            ResponseCodeHandler.HANDLE_404.handleRequest(exchange);
        }
    }

    private void handlInvalidMethod(final HttpServerExchange exchange) throws Exception {
        final HttpHandler localInvalidMethodHandler = this.invalidMethodHandler;
        if (localInvalidMethodHandler != null) {
            localInvalidMethodHandler.handleRequest(exchange);
        } else {
            handleFallback(exchange);
        }
    }

    private void handleNoMatch(
            final Routers routers,
            final HttpServerExchange exchange
    ) throws Exception {
        final PathTemplateRouter.RouteResult<Object> routeResult = routers.allMethodsRouter
                .apply(exchange.getRelativePath());
        if (routeResult.getPathTemplate().isPresent()) {
            handlInvalidMethod(exchange);
        } else {
            handleFallback(exchange);
        }
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final Routers localRouters = this.routers;

        final PathTemplateRouter.Router<RoutingMatch> methodRouter = localRouters.methodRouters
                .get(exchange.getRequestMethod());
        if (methodRouter == null) {
            handleNoMatch(localRouters, exchange);
            return;
        }

        final PathTemplateRouter.RouteResult<RoutingMatch> routeResult = methodRouter.apply(exchange.getRelativePath());
        if (routeResult.getPathTemplate().isEmpty()) {
            handleNoMatch(localRouters, exchange);
            return;
        }

        exchange.putAttachment(PathTemplateMatch.ATTACHMENT_KEY, routeResult);
        if (rewriteQueryParameters) {
            for (Map.Entry<String, String> entry : routeResult.getParameters().entrySet()) {
                exchange.addQueryParam(entry.getKey(), entry.getValue());
            }
        }

        for (final HandlerHolder handler : routeResult.getTarget().predicateHandlers) {
            if (handler.predicate.resolve(exchange)) {
                handler.handler.handleRequest(exchange);
                return;
            }
        }

        if (routeResult.getTarget().defaultHandler != null) {
            routeResult.getTarget().defaultHandler.handleRequest(exchange);
            return;
        }

        handleFallback(exchange);
    }

    private PathTemplateRouter.Builder<RoutingMatchBuilder, RoutingMatch> getOrAddMethodRouterBuiler(
            final HttpString method
    ) {
        Objects.requireNonNull(method);

        PathTemplateRouter.Builder<RoutingMatchBuilder, RoutingMatch> result = methodRouterBuilders.get(method);
        if (result == null) {
            result = PathTemplateRouter.Builder.newBuilder().updateDefaultTargetFactory(noRoutingMatchBuilder);
            methodRouterBuilders.put(method, result);
        }
        return result;
    }

    private RoutingMatchBuilder getOrAddMethodRoutingMatchBuilder(
            final HttpString method,
            final String template
    ) {
        Objects.requireNonNull(template);

        final PathTemplateRouter.Builder<RoutingMatchBuilder, RoutingMatch> routeBuilder
                = getOrAddMethodRouterBuiler(method);
        final PathTemplateRouter.Template<RoutingMatchBuilder> parsedTemplate = PathTemplateRouter.parseTemplate(
                template, new RoutingMatchBuilder()
        );

        final RoutingMatchBuilder existing = routeBuilder.getTemplateTarget(parsedTemplate);
        if (existing != null) {
            return existing;
        }

        routeBuilder.addTemplate(parsedTemplate);
        return parsedTemplate.getTarget();
    }

    private Map<HttpString, PathTemplateRouter.Router<RoutingMatch>> createMethodRouters() {
        final Map<HttpString, PathTemplateRouter.Router<RoutingMatch>> result = new HashMap<>(
                (int) (methodRouterBuilders.size() / 0.75d) + 1
        );
        for (final Entry<HttpString, PathTemplateRouter.Builder<RoutingMatchBuilder, RoutingMatch>> entry
                : methodRouterBuilders.entrySet()) {
            result.put(entry.getKey(), entry.getValue().build());
        }
        return Collections.unmodifiableMap(result);
    }

    private static <A> Consumer<PathTemplateRouter.Template<A>> createAddTemplateIfAbsentConsumer(
            final PathTemplateRouter.SimpleBuilder<Object> builder,
            final Supplier<Object> targetFactory
    ) {
        Objects.requireNonNull(builder);
        Objects.requireNonNull(targetFactory);

        return (final PathTemplateRouter.Template<A> item) -> {
            final String template = item.getPathTemplate();
            final PathTemplateRouter.Template<Supplier<Object>> parsedTemplate = PathTemplateRouter.parseTemplate(
                    template, targetFactory
            );
            final PathTemplateRouter.PatternEqualsAdapter<PathTemplateRouter.Template<Supplier<Object>>> parsedTemplatePattern
                    = new PathTemplateRouter.PatternEqualsAdapter<>(parsedTemplate);
            if (!builder.getTemplates().containsKey(parsedTemplatePattern)) {
                builder.getTemplates().put(parsedTemplatePattern, parsedTemplate.getTarget());
            }
        };
    }

    private PathTemplateRouter.Router<Object> createAllMethodsRouter() {
        final Object target = new Object();
        final Supplier<Object> targetFactory = () -> target;
        final PathTemplateRouter.SimpleBuilder<Object> builder = PathTemplateRouter.SimpleBuilder
                .newBuilder(target);
        methodRouterBuilders.values().stream()
                .flatMap(b -> b.getTemplates().keySet().stream())
                .map(PathTemplateRouter.PatternEqualsAdapter::getElement)
                .forEach(createAddTemplateIfAbsentConsumer(builder, targetFactory));
        return builder.build();
    }

    private RoutingHandler build() {
        this.routers = new Routers(
                createMethodRouters(),
                createAllMethodsRouter()
        );
        return this;
    }

    public synchronized RoutingHandler add(final HttpString method, final String template, final HttpHandler handler) {
        getOrAddMethodRoutingMatchBuilder(method, template).defaultHandler = handler;
        return build();
    }

    public synchronized RoutingHandler add(final String method, final String template, final HttpHandler handler) {
        return add(new HttpString(method), template, handler);
    }

    public synchronized RoutingHandler get(final String template, final HttpHandler handler) {
        return add(Methods.GET, template, handler);
    }

    public synchronized RoutingHandler post(final String template, final HttpHandler handler) {
        return add(Methods.POST, template, handler);
    }

    public synchronized RoutingHandler put(final String template, final HttpHandler handler) {
        return add(Methods.PUT, template, handler);
    }

    public synchronized RoutingHandler delete(final String template, final HttpHandler handler) {
        return add(Methods.DELETE, template, handler);
    }

    public synchronized RoutingHandler add(
            final HttpString method,
            final String template,
            final Predicate predicate,
            final HttpHandler handler
    ) {
        getOrAddMethodRoutingMatchBuilder(method, template).predicateHandlers.add(
                new HandlerHolder(predicate, handler)
        );
        return build();
    }

    public synchronized RoutingHandler add(
            final String method,
            final String template,
            final Predicate predicate,
            final HttpHandler handler
    ) {
        return add(new HttpString(method), template, predicate, handler);
    }

    public synchronized RoutingHandler get(
            final String template,
            final Predicate predicate,
            final HttpHandler handler
    ) {
        return add(Methods.GET, template, predicate, handler);
    }

    public synchronized RoutingHandler post(
            final String template,
            final Predicate predicate,
            final HttpHandler handler
    ) {
        return add(Methods.POST, template, predicate, handler);
    }

    public synchronized RoutingHandler put(
            final String template,
            final Predicate predicate,
            final HttpHandler handler
    ) {
        return add(Methods.PUT, template, predicate, handler);
    }

    public synchronized RoutingHandler delete(
            final String template,
            final Predicate predicate,
            final HttpHandler handler
    ) {
        return add(Methods.DELETE, template, predicate, handler);
    }

    public synchronized RoutingHandler addAll(RoutingHandler routingHandler) {
        /* This method does not do exactly what the original method used to do.  The original implementation
        performed a shallow copy of the underlying matcher, which would result in mutable instances of the
        (originally mutable) RoutingMatch class being held by both this RoutingHandler and the original
        RoutingHandler.  Since the original fields - specifically RoutingMatch.defaultHandler - were not marked
        as volatile, that could result in the handle method of this handler using outdated / cached values for
        the field. Since the new PathTemplateRouter is immutable, there is a requirement to rebuild it whenever
        its configuration (templates etc) are mutated.  Mutating via the original RoutingHandler would - after
        having called this method - also result in the router being out of sync with the router builder.
        For these reasons, this has been changed to a deep copy.  Arguably, developers won't expect to end up with
        two RoutingHandlers that are implicitely linked after having called this method anyway. */
        synchronized (routingHandler) {
            for (final Entry<HttpString, PathTemplateRouter.Builder<RoutingMatchBuilder, RoutingMatch>> outer
                    : routingHandler.methodRouterBuilders.entrySet()) {
                final PathTemplateRouter.Builder<RoutingMatchBuilder, RoutingMatch> builder
                        = getOrAddMethodRouterBuiler(outer.getKey());
                for (final Entry<PathTemplateRouter.PatternEqualsAdapter<PathTemplateRouter.Template<RoutingMatchBuilder>>, RoutingMatchBuilder> inner
                        : outer.getValue().getTemplates().entrySet()) {
                    builder.addTemplate(
                            inner.getKey().getElement().getPathTemplate(),
                            inner.getKey().getElement().getTarget().deepCopy()
                    );
                }
            }
        }
        return build();
    }

    private boolean removeIfPresent(final HttpString method, final String path) {
        Objects.requireNonNull(method);
        Objects.requireNonNull(path);

        final PathTemplateRouter.Builder<RoutingMatchBuilder, RoutingMatch> builder = methodRouterBuilders.get(method);
        if (builder == null) {
            return false;
        }

        final PathTemplateRouter.Template<RoutingMatchBuilder> parsedTemplate = PathTemplateRouter.parseTemplate(
                path, noRoutingMatchBuilder
        );
        final PathTemplateRouter.PatternEqualsAdapter<PathTemplateRouter.Template<RoutingMatchBuilder>> parsedTemplatePattern
                = new PathTemplateRouter.PatternEqualsAdapter<>(parsedTemplate);

        if (!builder.getTemplates().containsKey(parsedTemplatePattern)) {
            return false;
        }

        builder.getTemplates().remove(parsedTemplatePattern);

        if (builder.getTemplates().isEmpty()) {
            methodRouterBuilders.remove(method);
        }

        return true;
    }

    /**
     *
     * Removes the specified route from the handler
     *
     * @param method The method to remove
     * @param path the path template to remove
     *
     * @return this handler
     */
    public synchronized RoutingHandler remove(final HttpString method, final String path) {
        return removeIfPresent(method, path) ? build() : this;
    }

    /**
     * Removes the specified route from the handler
     *
     * @param path the path template to remove
     *
     * @return this handler
     */
    public synchronized RoutingHandler remove(final String path) {
        Objects.requireNonNull(path);

        boolean removed = false;
        for (final Entry<HttpString, PathTemplateRouter.Builder<RoutingMatchBuilder, RoutingMatch>> entry
                : methodRouterBuilders.entrySet()) {
            removed = removeIfPresent(entry.getKey(), path) || removed;
        }

        return removed ? build() : this;
    }

    /**
     * @return Handler called when no match was found and invalid method handler can't be invoked.
     */
    public HttpHandler getFallbackHandler() {
        return fallbackHandler;
    }

    /**
     * @param fallbackHandler Handler that will be called when no match was found and invalid method handler can't be invoked.
     *
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
     * Sets the handler called when this instance can not match the http method but can match another http method. For example:
     * For an exchange the POST method is not matched by this instance but at least one http method matched for the exchange. If
     * this handler is null the fallbackHandler will be used.
     *
     * @param invalidMethodHandler Handler that will be called when this instance can not match the http method but can match
     * another http method.
     *
     * @return This instance.
     */
    public RoutingHandler setInvalidMethodHandler(HttpHandler invalidMethodHandler) {
        this.invalidMethodHandler = invalidMethodHandler;
        return this;
    }
}
