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

import java.io.IOException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

/**
 * Test error handling behaviour
 *
 * @author Stuart Douglas
 */
public class ProgramaticErrorEndpoint extends Endpoint {


    private static final BlockingDeque<String> QUEUE = new LinkedBlockingDeque<>();

    public static String getMessage() {
        try {
            return QUEUE.poll(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        session.addMessageHandler(new MessageHandler.Whole<String>() {

            @Override
            public void onMessage(String message) {

                QUEUE.add(message);
                if (message.equals("app-error")) {
                    throw new RuntimeException("an error");
                } else if (message.equals("io-error")) {
                    throw new RuntimeException(new IOException());
                }
            }
        });
    }

    @Override
    public void onError(Session session, Throwable thr) {
        QUEUE.add("ERROR: " + thr.getClass().getName());
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        QUEUE.add("CLOSED");
    }
}
