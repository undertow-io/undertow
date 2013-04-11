/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.impl;

import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.api.WebSocketSessionIdGenerator;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * {@link WebSocketConnectionCallback} which will create a {@link io.undertow.websockets.api.WebSocketSession} and operate on it.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocketSessionConnectionCallback implements WebSocketConnectionCallback {
    private final WebSocketSessionIdGenerator idGenerator;
    private final WebSocketSessionHandler sessionHandler;
    private final boolean executeInIoThread;

    public WebSocketSessionConnectionCallback(WebSocketSessionHandler sessionHandler) {
        this(new UuidWebSocketSessionIdGenerator(), sessionHandler, false);
    }


    public WebSocketSessionConnectionCallback(WebSocketSessionIdGenerator idGenerator, WebSocketSessionHandler sessionHandler) {
        this(idGenerator, sessionHandler, false);
    }

    public WebSocketSessionConnectionCallback(WebSocketSessionIdGenerator idGenerator, WebSocketSessionHandler sessionHandler, boolean executeInIoThread) {
        this.idGenerator = idGenerator;
        this.sessionHandler = sessionHandler;
        this.executeInIoThread = executeInIoThread;
    }

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        final WebSocketChannelSession session = new WebSocketChannelSession(channel, idGenerator.nextId(), executeInIoThread);
        sessionHandler.onSession(session, exchange);
        WebSocketRecieveListeners.startRecieving(session, channel, executeInIoThread);
    }


}
