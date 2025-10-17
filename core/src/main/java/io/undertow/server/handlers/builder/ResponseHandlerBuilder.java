/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ResponseHandler;

/**
 * @author <a href="mailto:bbaranow@redhat.com">Bartosz Baranowski</a>
 */
public class ResponseHandlerBuilder implements HandlerBuilder {
    @Override
    public String name() {
        return "response";
    }

    @Override
    public Map<String, Class<?>> parameters() {
        Map<String, Class<?>> parameters = new HashMap<>();
        parameters.put("code", Integer.class);
        parameters.put("reason", String.class);
        parameters.put("type", String.class);
        parameters.put("body", String.class);
        return parameters;
    }

    @Override
    public Set<String> requiredParameters() {
        final Set<String> req = new HashSet<>();
        req.add("code");
        return req;
    }

    @Override
    public String defaultParameter() {
        // default parameter - name(paramValue) not supported
        return null;
    }

    @Override
    public HandlerWrapper build(final Map<String, Object> config) {
        final Integer code = (Integer) config.get("code");
        final String reason = (String) config.get("reason");
        final String type = (String) config.get("type");
        final String body = (String) config.get("body");
        return new HandlerWrapper() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                if (body == null) {
                    return new ResponseHandler(code, reason);
                } else {
                    if (type == null) {
                        return new ResponseHandler(code, reason, body);
                    } else {
                        return new ResponseHandler(code, reason, body, type);
                    }
                }
            }
        };
    }
}
