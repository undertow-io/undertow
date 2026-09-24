/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.jsr.test.limit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.undertow.UndertowOptions;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.RemoteEndpoint;

public class TestBinaryDefaultLimitOverFlow extends TestMessageLimitBase {

    private static final byte[] MESSAGE;
    static {
        MESSAGE = new byte[UndertowOptions.WEB_SOCKET_DEFAULT_MAX_MESSAGE_SIZE_BINARY+1];
        new Random().nextBytes(MESSAGE);
    }
    @Override
    protected List<Event> getExpectedEvents() {
        final Event me = new Event(new IOException(WebSocketMessages.MESSAGES.messageToBig(UndertowOptions.WEB_SOCKET_DEFAULT_MAX_MESSAGE_SIZE_BINARY)), 0);
        final CloseReason reason = new CloseReason(CloseCodes.TOO_BIG, WebSocketMessages.MESSAGES.messageToBig(UndertowOptions.WEB_SOCKET_DEFAULT_MAX_MESSAGE_SIZE_BINARY));
        final Event overflow = new Event(reason,1);
        ArrayList<Event> lst = new ArrayList<TestMessageLimitBase.Event>(2);
        lst.add(me);
        lst.add(overflow);
        return lst;
    }

    @Override
    protected void configure(WebSocketDeploymentInfo info) {
        //NOP
    }

    @Override
    protected Sender getSender() {
        return (a,b)->{
            RemoteEndpoint.Basic rem = a.getBasicRemote();
            rem.sendBinary(ByteBuffer.wrap(MESSAGE));
        };
    }

    @Override
    protected Class<?> getEndpoint() {
        return MyEndpoint.class;
    }

    @Override
    protected String getEndpointURLPart() {
        return "/webSocketBinaryDefaultLimitOverFlow";
    }

    @ServerEndpoint("/webSocketBinaryDefaultLimitOverFlow")
    protected static class MyEndpoint extends TestMessageLimitBase.WebSocketBaseClass{};
}
