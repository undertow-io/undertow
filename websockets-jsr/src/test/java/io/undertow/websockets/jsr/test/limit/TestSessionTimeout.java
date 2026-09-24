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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import io.undertow.testutils.DefaultServer;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import io.undertow.websockets.jsr.test.limit.TestMessageLimitBase.WebSocketBaseClass;
import jakarta.servlet.ServletException;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

public class TestSessionTimeout extends TestMessageLimitBase {

    private static final String MESSAGE = "It's not about the money, it's about the message!!!";
    private static final int SESSION_TIMEOUT = 5000;
    private static CountDownLatch LATCH = new CountDownLatch(1); // thats iffy....

    @Override
    public void setup() throws ServletException {
        LATCH = new CountDownLatch(1);
        super.setup();
    }

    @Override
    @Test
    public void testLimit() throws Exception {

        List<TestMessageLimitBase.Event> events = WebSocketBaseClass.events;
        final ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        final Session s = ContainerProvider.getWebSocketContainer().connectToServer(new LimitsEndpoint(getSender()),
                clientEndpointConfig, new URI(DefaultServer.getDefaultServerURL() + getEndpointURLPart()));
        LATCH.await(1000, TimeUnit.MILLISECONDS);
        Thread.currentThread().sleep(SESSION_TIMEOUT + 1000);
        try {
            s.getBasicRemote().sendText("In the end it doesnt even matter!!!!");
            fail();
        } catch (java.io.IOException e) {
        }
        Thread.currentThread().sleep(2000); // just in case
        WebSocketBaseClass.finished = true;
        List<TestMessageLimitBase.Event> expectedEvents = getExpectedEvents();
        Assert.assertEquals(printEvents(expectedEvents, events), expectedEvents.size(), events.size());
        final int numberOfEvents = expectedEvents.size();
        for (int i = 0; i < numberOfEvents; i++) {
            Assert.assertEquals(expectedEvents.get(i), events.get(i));
        }
    }

    @Override
    protected List<Event> getExpectedEvents() {
        final Event me = new Event(MESSAGE, 0);
        final CloseReason reason = new CloseReason(CloseCodes.CLOSED_ABNORMALLY, null);
        final Event timeout = new Event(reason, 1);
        ArrayList<Event> lst = new ArrayList<TestMessageLimitBase.Event>(2);
        lst.add(me);
        lst.add(timeout);
        return lst;
    }

    @Override
    protected void configure(WebSocketDeploymentInfo info) {
        // NOP
        info.setDefaultMaxSessionIdleTimeout(SESSION_TIMEOUT / 1000); // 5s
    }

    @Override
    protected Sender getSender() {
        return (a, b) -> {
            RemoteEndpoint.Basic rem = a.getBasicRemote();
            rem.sendText(MESSAGE);
        };
    }

    protected Class<?> getEndpoint() {
        return SessionTimeouTendpoint.class;
    }

    @Override
    protected String getEndpointURLPart() {
        return "/webSockeSessionTimeout";
    }

    @ServerEndpoint("/webSockeSessionTimeout") // This is not inheritable ?
    protected static class SessionTimeouTendpoint extends TestMessageLimitBase.WebSocketBaseClass {

        @Override
        public void onMessage(ByteBuffer dataBuffer, Session session) throws IOException {
            super.onMessage(dataBuffer, session);
            LATCH.countDown();
        }

        @Override
        public void onMessage(String dataBuffer, Session session) throws IOException {
            super.onMessage(dataBuffer, session);
            LATCH.countDown();
        }

        @Override
        public void onError(Throwable t, Session s) {
            super.onError(t, s);
            LATCH.countDown();
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            super.onClose(session, closeReason);
            LATCH.countDown();
        }

    }
}
