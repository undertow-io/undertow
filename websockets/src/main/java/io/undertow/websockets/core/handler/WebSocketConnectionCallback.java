/*
 * Copyright 2012 JBoss, by Red Hat, Inc
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

package io.undertow.websockets.core.handler;

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.core.WebSocketChannel;

/**
 * Interface that is used on the client side to accept web socket connections
 *
 * @author Stuart Douglas
 */
public interface WebSocketConnectionCallback {

    /**
     * Is called once the WebSocket connection is established, which means the handshake was successful.
     *
     */
    void onConnect(HttpServerExchange exchange, WebSocketChannel channel);

}
