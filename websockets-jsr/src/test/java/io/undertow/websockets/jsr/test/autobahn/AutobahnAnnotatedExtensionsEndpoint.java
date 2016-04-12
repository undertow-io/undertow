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
import java.util.List;

import javax.websocket.Extension;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

/**
 * An Endpoint class to be used in Autobahn test suite.
 * <p>
 * A variant of {@link io.undertow.websockets.jsr.test.autobahn.AutobahnAnnotatedEndpoint} .
 *
 * @author Stuart Douglas
 * @author Lucas Ponce
 */
@ServerEndpoint(value = "/", configurator = AutobahnAnnotatedExtensionsEndpoint.Config.class)
public class AutobahnAnnotatedExtensionsEndpoint {

    public static class Config extends ServerEndpointConfig.Configurator {
        @Override
        public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
            return super.getNegotiatedExtensions(installed, requested);
        }
    }

    Writer writer;
    OutputStream stream;
    int txtCount = 0;
    int binCount = 0;

    @OnMessage
    public void handleMessage(final String message, Session session, boolean last) throws IOException {
        if (writer == null) {
            writer = session.getBasicRemote().getSendWriter();
        }
        writer.write(message);
        if (last) {
            txtCount++;
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
            binCount++;
            stream.close();
            stream = null;
        }
    }
}
