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

import java.util.Collections;
import java.util.List;

import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.server.ServerEndpoint;

public class TestTextNewLimitRespected extends TestMessageLimitBase {

    private static final String MESSAGE;
    static {
        StringBuilder stringBuilder = new StringBuilder();

        for(int i = 0; i< 1024; i++) {
            stringBuilder.append(i%2);
        }

        MESSAGE = stringBuilder.toString();
    }

    private static void primeMessage(byte[] data) {
        for(int i = 0; i< 1024;i++) {
            data[i]=(byte)i;
        }
    }

    @Override
    protected List<Event> getExpectedEvents() {
        //some class loading shenanigans cause it to randomize ?
        final Event me = new Event(MESSAGE, 0);
        return Collections.singletonList(me);
    }

    @Override
    protected void configure(WebSocketDeploymentInfo info) {
        // NOP
        info.setDefaultMaxTextMessageBufferSize(1024); // 1KB
    }

    @Override
    protected Sender getSender() {
        return (a,b)->{
            RemoteEndpoint.Basic rem = a.getBasicRemote();
            rem.sendText(MESSAGE);
        };
    }

    @Override
    protected Class<?> getEndpoint() {
        return MyEndpoint.class;
    }

    @Override
    protected String getEndpointURLPart() {
        return "/webSockeTextNewLimitRespected";
    }

    @ServerEndpoint("/webSockeTextNewLimitRespected")
    protected static class MyEndpoint extends TestMessageLimitBase.WebSocketBaseClass{};
}
