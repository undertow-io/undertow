/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.websockets.jsr.test;

import java.net.URI;

import javax.net.ssl.SSLContext;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;

import io.netty.channel.EventLoopGroup;
import io.undertow.testutils.DefaultServer;
import io.undertow.websockets.jsr.WebsocketClientSslProvider;

public class TestSslProvider implements WebsocketClientSslProvider {
    @Override
    public SSLContext getSsl(EventLoopGroup worker, Class<?> annotatedEndpoint, URI uri) {
        return DefaultServer.getClientSSLContext();
    }

    @Override
    public SSLContext getSsl(EventLoopGroup worker, Object annotatedEndpointInstance, URI uri) {
        return DefaultServer.getClientSSLContext();
    }

    @Override
    public SSLContext getSsl(EventLoopGroup worker, Endpoint endpoint, ClientEndpointConfig cec, URI uri) {
        return DefaultServer.getClientSSLContext();
    }
}
