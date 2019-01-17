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

package io.undertow.websockets.jsr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.websocket.Extension;

import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;

/**
 * @author Stuart Douglas
 */
public class ExtensionImpl implements Extension {

    private final WebSocketExtensionData data;
    private final List<Parameter> parameters = new ArrayList<>();

    public ExtensionImpl(WebSocketExtensionData data) {
        this.data = data;
        for(Map.Entry<String, String> i : data.parameters().entrySet()) {
            parameters.add(new ParameterImpl(i.getKey(), i.getValue()));
        }
    }

    public WebSocketExtensionData getData() {
        return data;
    }

    @Override
    public String getName() {
        return data.name();
    }

    @Override
    public List<Parameter> getParameters() {
        return parameters;
    }

    public static class ParameterImpl implements Parameter {
        private final String name;
        private final String value;

        public ParameterImpl(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
