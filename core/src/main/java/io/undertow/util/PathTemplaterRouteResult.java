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

import io.undertow.server.HttpServerExchange;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Result from a routing request.
 *
 * This class extends {@link PathTemplateMatch} to allow adding instances of this class as attachments to
 * {@link HttpServerExchange} without having to copy the underlying values into a new instance of {@link PathTemplateMatch}. The
 * {@link PathTemplateMatch} class is widely referenced by code that uses Undertow - to obtain path parameters etc. This class
 * is therefore required to extend {@link PathTemplateMatch} for backwards compatibility.
 *
 * Instances of this class are thread-safe.
 *
 * @author Dirk Roets
 *
 * @param <T> Target type.
 */
public class PathTemplaterRouteResult<T> extends PathTemplateMatch {

    private final T target;
    private final Optional<String> pathTemplate;

    public PathTemplaterRouteResult(
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
     * @return The matched template, if any.
     */
    public Optional<String> getPathTemplate() {
        return pathTemplate;
    }
}
