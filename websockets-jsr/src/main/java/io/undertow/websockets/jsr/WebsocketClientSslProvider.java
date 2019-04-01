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

package io.undertow.websockets.jsr;

import java.net.URI;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;

import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;

/**
 * Interface that is loaded from a service loader, that allows
 * you to configure SSL for web socket client connections.
 *
 * @author Stuart Douglas
 */
public interface WebsocketClientSslProvider {

    SslContext getSsl(EventLoopGroup worker, final Class<?> annotatedEndpoint, URI uri);

    SslContext getSsl(EventLoopGroup worker, final Object annotatedEndpointInstance, URI uri);

    SslContext getSsl(EventLoopGroup worker, final Endpoint endpoint, final ClientEndpointConfig cec, URI uri);

}
