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
import io.undertow.server.HttpServerExchange;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Result for routing a requested URL path to a set of URL path templates using a {@link PathTemplateRouter}. Results for
 * requests that matched underlying templates will contain the original URL path template string in {@link #getPathTemplate() }.
 * Results for requests that did not match any underlying templates will have an empty Optional in {@link #getPathTemplate() }.
 *
 * This class extends {@link PathTemplateMatch} to allow adding instances of this class as attachments to
 * {@link HttpServerExchange} without having to copy the underlying values into a new instance of {@link PathTemplateMatch}. The
 * {@link PathTemplateMatch} class is widely referenced by code that uses Undertow - to obtain path parameters etc. This class
 * is therefore required to extend {@link PathTemplateMatch} for backwards compatibility.
 *
 * Thread-safety depends on the underlying map of parameters. If the map is immutable or otherwise thread-safe, then the
 * instance will be thread-safe as all other fields are immutable.
 *
 * This class uses a type parameter for the resulting {@link #getTarget() } in order to support different use cases that require
 * routing of URL paths to URL path templates, i.e.:
 * <ul>
 * <li>Routing requests to {@link HttpHandler}s for RESTful web service: {@link #getTarget() } will typically contain the target
 * / destination {@link HttpHandler}.</li>
 * <li>Processing HTTP access logs for reporting or monitoring purposes: {@link #getTarget() } may contain an object used to
 * aggregate metrics for the specific template.</li>
 * </ul>
 *
 * @author Dirk Roets
 *
 * @param <T> Target type.
 */
public class PathTemplateRouteResult<T> extends PathTemplateMatch {

    private final T target;
    private final Optional<String> pathTemplate;

    /**
     * @param target The target.
     * @param pathTemplate The URL path template that was matched. If no URL path templates matched the request, then this
     * Optional will be empty.
     * @param parameters The path parameters that were matched by the pathTemplate.
     */
    public PathTemplateRouteResult(
            final T target,
            final Optional<String> pathTemplate,
            final Map<String, String> parameters
    ) {
        super(
                pathTemplate.orElse(""),
                Objects.requireNonNull(parameters)
        );
        this.target = Objects.requireNonNull(target);
        this.pathTemplate = Objects.requireNonNull(pathTemplate);
    }

    @Override
    public String toString() {
        return "PathTemplateRouteResult{" + "target=" + target + ", pathTemplate=" + pathTemplate
                + ", paramValues=" + getParameters() + '}';
    }

    /**
     * @return The target.
     */
    public T getTarget() {
        return target;
    }

    /**
     * @return The URL path template that was matched. If no URL path templates matched the request, then this Optional will be
     * empty.
     */
    public Optional<String> getPathTemplate() {
        return pathTemplate;
    }
}
