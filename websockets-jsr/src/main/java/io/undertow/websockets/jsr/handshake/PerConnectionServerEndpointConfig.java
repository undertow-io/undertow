/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.jsr.handshake;

import jakarta.websocket.Decoder;
import jakarta.websocket.Encoder;
import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpointConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class PerConnectionServerEndpointConfig implements ServerEndpointConfig {

    private final ServerEndpointConfig delegate;
    private final Map<String, Object> userProperties;

    PerConnectionServerEndpointConfig(final ServerEndpointConfig delegate) {
        this.delegate = delegate;
        this.userProperties = Collections.synchronizedMap(new HashMap<>(delegate.getUserProperties()));
    }

    @Override
    public Class<?> getEndpointClass() {
        return delegate.getEndpointClass();
    }

    @Override
    public String getPath() {
        return delegate.getPath();
    }

    @Override
    public List<String> getSubprotocols() {
        return delegate.getSubprotocols();
    }

    @Override
    public List<Extension> getExtensions() {
        return delegate.getExtensions();
    }

    @Override
    public Configurator getConfigurator() {
        return delegate.getConfigurator();
    }

    @Override
    public List<Class<? extends Encoder>> getEncoders() {
        return delegate.getEncoders();
    }

    @Override
    public List<Class<? extends Decoder>> getDecoders() {
        return delegate.getDecoders();
    }

    @Override
    public Map<String, Object> getUserProperties() {
        return userProperties;
    }

}
