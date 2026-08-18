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
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.server.ServerEndpoint;

public class TestBinaryNewLimitOverFlow extends TestMessageLimitBase {

    private static final byte[] MESSAGE;
    static {
        MESSAGE = new byte[1024 + 1];
        new Random().nextBytes(MESSAGE);
    }

    @Override
    protected List<Event> getExpectedEvents() {
        final Event[] events = new Event[2];

        final Event first = new Event(new IOException(WebSocketMessages.MESSAGES.messageToBig(1024)), 0);
        final CloseReason reason = new CloseReason(CloseCodes.TOO_BIG, WebSocketMessages.MESSAGES.messageToBig(1024));
        final Event overflow = new Event(reason,1);
        events[0] = first;
        events[1] = overflow;

        return Arrays.asList(events);
    }

    @Override
    protected void configure(WebSocketDeploymentInfo info) {
        // NOP
        info.setDefaultMaxBinaryMessageBufferSize(1024); // 1KB
    }

    @Override
    protected Sender getSender() {
        return (a, b) -> {
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
        return "/webSocketBinaryNewLimitOverFlow";
    }

    @ServerEndpoint("/webSocketBinaryNewLimitOverFlow")
    protected static class MyEndpoint extends TestMessageLimitBase.WebSocketBaseClass{};
}
