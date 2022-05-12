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

package io.undertow.websockets.jsr.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

import io.undertow.testutils.DefaultServer;

/**
 * @author Andrej Golovnin
 */
public final class BinaryPartialEndpoint extends Endpoint {

    @Override
    public void onOpen(final Session session, EndpointConfig config) {
        session.addMessageHandler(new MessageHandler.Partial<byte[]>() {

            private ByteArrayOutputStream buffer;

            @Override
            public void onMessage(byte[] bytes, boolean last) {
                if (last) {
                    if (buffer == null) {
                        onRequest(bytes);
                    } else {
                        try {
                            buffer(bytes);
                            byte[] tmp = buffer.toByteArray();
                            onRequest(tmp);
                        } finally {
                            buffer = null;
                        }
                    }
                } else {
                    buffer(bytes);
                }
            }

            private void onRequest(final byte[] bytes) {
                // Just return the received bytes for the test
                DefaultServer.getWorker().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            session.getBasicRemote().sendBinary(
                                    ByteBuffer.wrap(bytes));
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                });
            }

            private void buffer(byte[] data) {
                if (buffer == null) {
                    buffer = new ByteArrayOutputStream(8096);
                }
                buffer.write(data, 0, data.length);
            }

        });

    }
}
