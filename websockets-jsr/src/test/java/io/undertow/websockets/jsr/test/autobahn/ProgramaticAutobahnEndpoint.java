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

package io.undertow.websockets.jsr.test.autobahn;

import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class ProgramaticAutobahnEndpoint extends Endpoint {
    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        session.addMessageHandler(new BinaryMessageHandler(session));
        session.addMessageHandler(new TextMessageHandler(session));
    }

    private static class BinaryMessageHandler implements MessageHandler.Whole<ByteBuffer> {

        private final Session session;

        private BinaryMessageHandler(Session session) {
            this.session = session;
        }

        @Override
        public void onMessage(ByteBuffer byteBuffer) {
            session.getAsyncRemote().sendBinary(byteBuffer);
        }
    }

    private static class TextMessageHandler implements MessageHandler.Whole<String> {

        private final Session session;

        private TextMessageHandler(Session session) {
            this.session = session;
        }

        @Override
        public void onMessage(String byteBuffer) {
            session.getAsyncRemote().sendText(byteBuffer);
        }
    }
}
