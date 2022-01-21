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

package io.undertow.websockets.jsr.test.annotated;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.websocket.ClientEndpoint;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

/**
 * @author Stuart Douglas
 */
@ClientEndpoint(subprotocols = {"foo", "bar"})
public class AnnotatedClientEndpoint {

    private static final Deque<String> MESSAGES = new ConcurrentLinkedDeque<>();

    private volatile boolean open = false;

    public static String message() throws InterruptedException {
        long start = System.nanoTime();
        while (System.nanoTime() - start < 3_000_000_000L) {
            String result = MESSAGES.pollFirst();
            if (result != null) {
                return result;
            }
            Thread.sleep(1);
        }
        return null;
    }

    @OnOpen
    public void onOpen(final Session session) {
        session.getAsyncRemote().sendText("hi");
        this.open = true;
    }

    @OnMessage
    public void onMessage(final String message) {
        MESSAGES.add(message);
    }

    @OnClose
    public void onClose() {
        this.open = false;
        MESSAGES.add("CLOSED");
    }

    public boolean isOpen() {
        return open;
    }

    public static void reset() {
        MESSAGES.clear();
    }

}
