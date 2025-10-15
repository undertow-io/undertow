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

package io.undertow.websockets.jsr.test.stress;

import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Stuart Douglas
 */
@ServerEndpoint(value = "/stress", subprotocols = {"foo", "bar", "configured-proto"})
public class StressEndpoint {

    public static Set<String> MESSAGES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private volatile String closed;

    private OutputStream out;

    @OnMessage
    public void handleMessage(Session session, final String message) throws IOException {
        if(closed != null) {
            System.out.println("closed message " + closed);
        }
        if(message.equals("close")) {
            closed = Thread.currentThread().getName();
            session.close();
            return;
        }
        MESSAGES.add(message);
    }

    @OnMessage
    public void handleMessage(Session session, final ByteBuffer message, boolean last) throws IOException {
        if(out == null) {
            out = session.getBasicRemote().getSendStream();
        }
        byte[] data = new byte[message.remaining()];
        message.get(data);
        out.write(data);

        if(last) {
            out.close();
            out = null;
        } else {
            out.flush();
        }
    }

    @OnError
    public void onError(Throwable e) throws IOException {
        e.printStackTrace();
        if(out != null) {
            out.close();
        }
    }
}
