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

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.server.ServerEndpoint;

public class TestBinaryNewLimitRespected extends TestMessageLimitBase {

    private static final byte[] MESSAGE;
    static {
        MESSAGE = new byte[1024];
        primeMessage(MESSAGE);
    }

    private static void primeMessage(byte[] data) {
        for(int i = 0; i< 1024;i++) {
            data[i]=(byte)i;
        }
    }

    @Override
    protected List<Event> getExpectedEvents() {
        //some class loading shenanigans cause it to randomize ?
        primeMessage(MESSAGE);
        final Event me = new Event(TestBinaryNewLimitRespected.MESSAGE, 0);
        return Collections.singletonList(me);
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
        return "/webSockeBinaryNewLimitRespected";
    }

    @ServerEndpoint("/webSockeBinaryNewLimitRespected")
    protected static class MyEndpoint extends TestMessageLimitBase.WebSocketBaseClass{};
}
