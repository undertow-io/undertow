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

import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Stuart Douglas
 */
@ServerEndpoint(value = "/chat/{user}", subprotocols = {"foo", "bar", "configured-proto"})
public class MessageEndpoint {

    public static volatile CloseReason closeReason;
    private static volatile CountDownLatch closeLatch = new CountDownLatch(1);

    @OnMessage
    public String handleMessage(Session session, final String message, @PathParam("user") String user) {
        String proto = session.getNegotiatedSubprotocol();
        return message + " " + user + (proto.isEmpty() ? "" : " (protocol=" + proto + ")");
    }

    @OnClose
    public void close(CloseReason c) {
        closeReason = c;
        closeLatch.countDown();
    }

    public static  CloseReason getReason() throws InterruptedException {
        closeLatch.await(10, TimeUnit.SECONDS);
        return closeReason;
    }

    public static void reset() {
        closeLatch = new CountDownLatch(1);
        closeReason = null;
    }

}
