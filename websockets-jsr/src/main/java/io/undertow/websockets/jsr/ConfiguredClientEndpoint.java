/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.websockets.jsr;

import javax.websocket.ClientEndpointConfig;

import io.undertow.websockets.jsr.annotated.AnnotatedEndpointFactory;

/**
 * @author Stuart Douglas
 */
public class ConfiguredClientEndpoint {

    private final ClientEndpointConfig config;
    private final AnnotatedEndpointFactory factory;

    public ConfiguredClientEndpoint(final ClientEndpointConfig config, final AnnotatedEndpointFactory factory) {
        this.config = config;
        this.factory = factory;
    }

    public ClientEndpointConfig getConfig() {
        return config;
    }

    public AnnotatedEndpointFactory getFactory() {
        return factory;
    }
}
