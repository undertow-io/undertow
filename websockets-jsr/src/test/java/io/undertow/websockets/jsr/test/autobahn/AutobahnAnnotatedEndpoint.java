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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

/**
 * @author Stuart Douglas
 */
@ServerEndpoint("/")
public class AutobahnAnnotatedEndpoint {

    Writer writer;
    OutputStream stream;

    @OnMessage
    public void handleMessage(final String message, Session session, boolean last) throws IOException {
        if (writer == null) {
            writer = session.getBasicRemote().getSendWriter();
        }
        writer.write(message);
        if (last) {
            writer.close();
            writer = null;
        }
    }

    @OnMessage
    public void handleMessage(final byte[] message, Session session, boolean last) throws IOException {
        if (stream == null) {
            stream = session.getBasicRemote().getSendStream();
        }
        stream.write(message);
        stream.flush();
        if (last) {
            stream.close();
            stream = null;
        }
    }
}
