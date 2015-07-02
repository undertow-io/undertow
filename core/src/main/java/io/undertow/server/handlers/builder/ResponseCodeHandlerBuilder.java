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

package io.undertow.server.handlers.builder;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ResponseCodeHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class ResponseCodeHandlerBuilder implements HandlerBuilder {
    @Override
    public String name() {
        return "response-code";
    }

    @Override
    public Map<String, Class<?>> parameters() {
        Map<String, Class<?>> parameters = new HashMap<>();
        parameters.put("value", Integer.class);
        return parameters;
    }

    @Override
    public Set<String> requiredParameters() {
        final Set<String> req = new HashSet<>();
        req.add("value");
        return req;
    }

    @Override
    public String defaultParameter() {
        return "value";
    }

    @Override
    public HandlerWrapper build(final Map<String, Object> config) {
        final Integer value = (Integer) config.get("value");
        return new HandlerWrapper() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                return new ResponseCodeHandler(value);
            }
        };
    }
}
