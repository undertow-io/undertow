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
import java.util.Arrays;
import java.util.List;

import io.undertow.UndertowOptions;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.server.ServerEndpoint;

public class TestTextDefaultLimitOverFlow extends TestMessageLimitBase{

    private static final String MESSAGE;
    static {
        StringBuilder stringBuilder = new StringBuilder();

        for(int i = 0; i< UndertowOptions.WEB_SOCKET_DEFAULT_MAX_MESSAGE_SIZE_TEXT+1; i++) {
            stringBuilder.append(i%2);
        }

        MESSAGE = stringBuilder.toString();
    }

    @Override
    protected List<Event> getExpectedEvents() {
        final Event[] events = new Event[2];

        final Event second = new Event(new IOException(WebSocketMessages.MESSAGES.messageToBig(UndertowOptions.WEB_SOCKET_DEFAULT_MAX_MESSAGE_SIZE_TEXT)), 0);
        final CloseReason reason = new CloseReason(CloseCodes.TOO_BIG, WebSocketMessages.MESSAGES.messageToBig(UndertowOptions.WEB_SOCKET_DEFAULT_MAX_MESSAGE_SIZE_TEXT));
        final Event overflow = new Event(reason,1);
        events[0] = second;
        events[1] = overflow;
        return Arrays.asList(events);
    }

    @Override
    protected void configure(WebSocketDeploymentInfo info) {
        //NOP
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
        return "/webSockeTextDefaultLimitOverFlow";
    }

    @ServerEndpoint("/webSockeTextDefaultLimitOverFlow")
    protected static class MyEndpoint extends TestMessageLimitBase.WebSocketBaseClass{};
}
