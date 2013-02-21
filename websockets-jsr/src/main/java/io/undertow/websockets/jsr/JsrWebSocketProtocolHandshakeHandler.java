/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.core.handler.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.protocol.Handshake;

import javax.websocket.server.ServerEndpointConfiguration;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link WebSocketProtocolHandshakeHandler} implementation which takes care to add the right {@link Handshake} instances
 * to the mix and so support everything needed as specified in the SPEC.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class JsrWebSocketProtocolHandshakeHandler extends WebSocketProtocolHandshakeHandler {

    public JsrWebSocketProtocolHandshakeHandler(WebSocketConnectionCallback callback, ServerEndpointConfiguration... configs) {
        super(handshakes(configs), callback);
    }

    private static Set<Handshake> handshakes(ServerEndpointConfiguration... configs) {
        Set<Handshake> handshakes = new HashSet<Handshake>();
        for (ServerEndpointConfiguration config: configs) {
            handshakes.add(new JsrHybi07Handshake(config));
            handshakes.add(new JsrHybi08Handshake(config));
            handshakes.add(new JsrHybi13Handshake(config));
        }
        return handshakes;
    }
}
