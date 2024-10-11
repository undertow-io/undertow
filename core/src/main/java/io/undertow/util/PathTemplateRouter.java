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
package io.undertow.util;

import io.undertow.server.HttpHandler;

/**
 * Routes requested URL paths to sets of underlying URL path templates.
 *
 * <p>
 * The recommended approach - that will work for most common use cases - to create instances of this interface is to use
 * {@link PathTemplateRouterFactory.SimpleBuilder}.
 *
 * <p>
 * This is one of three main classes that participate in setting up and routing requested URL paths to URL path templates:
 * <ol>
 * <li>{@link PathTemplateParser} participates in the <i>*setup phase</i> by parsing formatted strings into URL path
 * templates.</li>
 * <li>{@link PathTemplateRouterFactory} participates in the <i>*setup phase</i> by creating router instances from sets of
 * parsed URL path templates.</li>
 * <li>{@link PathTemplateRouter} participates in the <i>*routing phase</i> by routing requested URL paths to sets of underlying
 * URL path templates.</li>
 * </ol>
 *
 * <p>
 * <b>Routing methodology</b>
 *
 * <p>
 * (See {@link PathTemplateParser} for URL path template examples and descriptions of segment types).
 * <ul>
 * <li>Routers can route requested URL paths to any number of underlying URL path templates, provided that the patterns for all
 * URL path templates are unique. Specifically the template strings {@code '/books/{bookId}/chapters'} and
 * {@code '/books/{bookName}/chapters'} uses different parameter names, but the patterns are the same and a router can therefore
 * only contain one of those templates.</li>
 * <li>Templates consist of segments in numbered positions and routers recognise 3 types of segments, namely: static segments,
 * (named) parameter segments and wildcard segments.</li>
 * <li>When multiple underlying URL path templates match a requested URL path, then the router picks the most specific template
 * that matches the path. Another way of looking at "most specific" is that it is the template that is the least likely to match
 * if something in the requested URL path were to change. For example:<ul>
 * <li>The templates {@code '/books/14/chapters'} and {@code '/books/{bookId}/chapters'} will both match the request
 * {@code '/books/14/chapters'} and in this case the routers will return the first template. Templates that contain more static
 * segments are always more specific than templates that contain less static segments.</li>
 * <li>The templates {@code '/books/{bookId}/chapters'} and {@code '/books/{bookId}/{partName}'} will both match the request
 * {@code '/books/14/chapters'} and in this case the routers will return the first template. This first template is once again
 * more specific as it contains more static segments.</li>
 * <li>The templates {@code '/books/14/{partName}'} and {@code '/books/{bookId}/chapters'} will both match the request
 * {@code '/books/14/chapters'} and in this case the routers will return the first template. Templates with static segments that
 * appear earlier in the templates are more specific than templates with static segments that appear later in the templates for
 * templates that contain the same number of static segments.</li>
 * <li>The templates {@code '/books/{bookId}'} and {@code '/books/*'} will both match the request {@code '/books/14'} and in
 * this case the routers will return the first template. Templates that contain wildcards are always less specific than
 * templates that do not contain wildcards.</li>
 * <li>The templates {@code '/books/*'} and {@code '/books*'} will both match the request {@code '/books/14'} and in this case
 * the routers will return the first template. The first template contains more static segments and is therefore considered more
 * specific.</li>
 * <li>The templates {@code '/books*'} and {@code '/*'} will both match the request {@code '/books/14'} and in this case the
 * routers will return the first template. Wildcards that contain prefixes are considered to be more specific than wildcards
 * that do not contain prefixes.</li>
 * </ul></li>
 * </ul>
 *
 * <p>
 * <b>Routing phases</b>
 *
 * <p>
 * <ol>
 * <li>It is assumed that most services that use routing based on path templates will setup a router once (when the service
 * starts) using a single thread and will then use that router to route millions of inbound requests using multiple concurrent
 * threads. Perhaps the setup will not happen exactly once, but the mutation of routes happen very few times when compared to
 * the number of times that requests are routed. For this reason this design is heavily biased towards optimising the
 * performance of routing requests - sometimes at the expense of the performance of mutating routes.</li>
 * <li>Taking point (1) above into consideration. Documentation and comments refer to two distinct phases of processing:<ol>
 * <li>"Setup phase" as the phase/process during which routes are mutated and a router instance is created.</li>
 * <li>"Routing phase" as the phase/process during which an existing router is used to route requests.</li>
 * </ol></li>
 * </ol>
 *
 * <p>
 * Implementations of this interface must be thread-safe.
 *
 * @author Dirk Roets
 *
 * @param <T> Target type.
 */
public interface PathTemplateRouter<T> {

    /**
     * @return The default target for requests that do not match any specific routes.
     */
    T getDefaultTarget();

    /**
     * Routes the requested URL path to the best available target from the set of underlying URL path templates.
     *
     * <p>
     * If the requested URL path matches any of the underlying URL path templates, then the most
     * specific match (target and template) will be returned in the result. If the requested URL path does not match any of the
     * underlying URL path templates, then the result will contain {@link #getDefaultTarget() } as the target and will contain an
     * empty Optional in {@link PathTemplateRouteResult#getPathTemplate() }.
     *
     * <p>
     * On completion of this method, the caller will have the best available target for the specified path. This method
     * merely provides the best target and does not call / action the target.  That remains the responsibility of the caller.
     * This design allows the router to support different use cases, i.e.:
     * <ol>
     * <li>Determine the best available {@link HttpHandler} to process a client request based on the requested path.</li>
     * <li>Summarise HTTP access logs by normalising requested paths to available path templates.</li>
     * </ol>
     *
     * @param path The requested URL path.
     *
     * @return The routing result.
     */
    PathTemplateRouteResult<T> route(String path);
}
