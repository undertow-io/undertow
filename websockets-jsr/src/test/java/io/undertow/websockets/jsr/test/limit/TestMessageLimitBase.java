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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.Handlers;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.servlet.ServletException;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@RunWith(DefaultServer.class)
@HttpOneOnly
public abstract class TestMessageLimitBase {

    private static DeploymentManager deploymentManager;

    @Before
    public void setup() throws ServletException {
        WebSocketBaseClass.events.clear();
        WebSocketBaseClass.finished = false;
        final ServletContainer container = ServletContainer.Factory.newInstance();

        final WebSocketDeploymentInfo websocketDeploymentInfo = new WebSocketDeploymentInfo();
        configure(websocketDeploymentInfo);
        DeploymentInfo builder = new DeploymentInfo().setClassLoader(TestMessageLimitBase.class.getClassLoader())
                .setContextPath("/").setResourceManager(new TestResourceLoader(TestMessageLimitBase.class))
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        websocketDeploymentInfo.setBuffers(DefaultServer.getBufferPool())
                                .setWorker(DefaultServer.getWorkerSupplier()).addEndpoint(getEndpoint()))
                .setDeploymentName("servletContext.war");

        deploymentManager = container.addDeployment(builder);
        deploymentManager.deploy();

        DefaultServer.setRootHandler(Handlers.path().addPrefixPath("/", deploymentManager.start()));
    }

    @After
    public void cleanup() throws ServletException {
        WebSocketBaseClass.events.clear();
        if (deploymentManager != null) {
            deploymentManager.stop();
            deploymentManager.undeploy();
        }
    }

    @Test
    public void testLimit() throws Exception {

        List<TestMessageLimitBase.Event> events = WebSocketBaseClass.events;
        final ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        ContainerProvider.getWebSocketContainer().connectToServer(new LimitsEndpoint(getSender()),
                clientEndpointConfig, new URI(DefaultServer.getDefaultServerURL() + getEndpointURLPart()));

        Thread.currentThread().sleep(2000);
        //this is hack to overcome GOING_AWAY randomness. This is triggered on DefaultServer going down.
        WebSocketBaseClass.finished = true;
        List<TestMessageLimitBase.Event> expectedEvents = getExpectedEvents();
        Assert.assertEquals(printEvents(expectedEvents, events), expectedEvents.size(), events.size());
        final int numberOfEvents = expectedEvents.size();
        for(int i = 0; i< numberOfEvents; i++) {
            Assert.assertEquals(expectedEvents.get(i), events.get(i));
        }

    }

    protected String printEvents(List<Event> expectedEvents, List<Event> receivedEvents) {
        final StringBuilder stringBuilder = new StringBuilder();
        final int maxIndex = Math.max(expectedEvents.size(), receivedEvents.size());
        for(int i = 0; i< maxIndex; i++) {
            Event expected = fetchEvent(expectedEvents,i);
            Event received = fetchEvent(receivedEvents, i);
            stringBuilder.append("\n=============[").append(i).append("]=============\n");
            stringBuilder.append(printEvent("Expected", expected));
            stringBuilder.append(printEvent("Received", received));
        }
        return stringBuilder.toString();
    }

    protected String printEvent(final String type, final Event event) {
        final StringBuilder stringBuilder = new StringBuilder();
        if (event != null) {
            stringBuilder.append(type).append(": \n")
            .append("Index: ").append(event.getIndex()).append("\n")
            .append("Message: ").append(formatMessage(event)).append("\n")
            .append("Binary: ").append(event.isBinary()).append("\n");

            if (event.getError() != null) {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final PrintStream ps = new PrintStream(baos);
                event.getError().printStackTrace(ps);
                ps.flush();
                stringBuilder.append("Error: ").append(new String(baos.toByteArray())).append("\n\n");
            } else {
                stringBuilder.append("Error: ").append(event.getError()).append("\n\n");
            }

        } else {
            stringBuilder.append(type).append(": \n")
            .append("Index: ").append("N/A").append("\n")
            .append("Message: ").append("N/A").append("\n")
            .append("Binary: ").append("N/A").append("\n")
            .append("Error: ").append("N/A").append("\n\n");
        }

        return stringBuilder.toString();
    }

    protected Event fetchEvent(final List<Event> list, final int index) {
        if (index < list.size()) {
            return list.get(index);
        } else {
            return null;
        }
    }

    protected static String formatMessage(final Event event) {
        if (event.getMessage() == null) {
            return "N/A";
        } else {
            final Object msg = event.getMessage();
            if (msg instanceof String) {
                final String data = (String) msg;
                return data.substring(0, 20 < data.length() ? 20 : data.length()) + "(" + data.length() + ")";
            } else if (msg instanceof byte[]) {
                byte[] data = (byte[]) msg;
                return Arrays.toString(data).substring(0, 20 < data.length ? 20 : data.length) + "(" + data.length + ")";
            } else if (event.getMessage() instanceof CloseReason) {
                CloseReason cr = (CloseReason) event.getMessage();
                return cr.getCloseCode() + "-" + cr.getReasonPhrase();
            } else {
                final String data = (String) msg.toString();
                return data.substring(0, 20 < data.length() ? 20 : data.length()) + "(" + data.length() + ")";
            }
        }
    }

    protected abstract List<Event> getExpectedEvents();

    protected abstract void configure(WebSocketDeploymentInfo info);

    protected abstract Sender getSender();

    protected abstract Class<?> getEndpoint();

    protected abstract String getEndpointURLPart();

    public static class Event {

        private int index;
        private Object message;
        private boolean binary;
        private Throwable error;

        public Event(Throwable error, int index) {
            super();
            this.index = index;
            this.error = error;
        }

        public Event(Object message, int index) {
            this.message = message;
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        public Object getMessage() {
            return message;
        }

        public boolean isBinary() {
            return binary;
        }

        public Throwable getError() {
            return error;
        }

        @Override
        public String toString() {
            return "Event [index=" + index + ", message=" + formatMessage(this) + ", binary=" + binary + ", error=" + error + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(error, index, message, binary);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Event other = (Event) obj;
            return Objects.equals(error == null ? "N/A" : error.getMessage(), other.error == null ? "N/A" : other.error.getMessage()) && index == other.index && Objects.deepEquals(formatMessage(this), formatMessage(other));
        }

    }

    @ServerEndpoint("/webSocket")
    public static class WebSocketBaseClass {

        protected int index = 0;
        protected static List<TestMessageLimitBase.Event> events = new ArrayList<TestMessageLimitBase.Event>(5);
        protected static boolean finished = true;
        @OnMessage
        public synchronized void onMessage(ByteBuffer dataBuffer, Session session) throws IOException {
            byte[] hd = new byte[dataBuffer.remaining()];
            dataBuffer.get(hd);
            events.add(new Event(hd, index++));
        }

        @OnMessage
        public synchronized void onMessage(String dataBuffer, Session session) throws IOException {
            events.add(new Event(dataBuffer, index++));
        }

        @OnError
        public synchronized void onError(Throwable t, Session s) {
            events.add(new Event(t, index++));
        }

        @OnClose
        public synchronized void onClose(final Session session, CloseReason closeReason) {
            if(finished) {
                return;
            }
            events.add(new Event(closeReason, index++));
        }
    }

    protected interface Sender{
        void sendMessages(Session session, EndpointConfig endpointConfig) throws Exception;
    }

    protected static class LimitsEndpoint extends Endpoint {
        private final Sender sender;
        LimitsEndpoint(final Sender sender) {
            this.sender = sender;
        }

        @Override
        public void onOpen(final Session session, final EndpointConfig endpointConfig) {

            try {
                this.sender.sendMessages(session, endpointConfig);
            } catch (Throwable t) {
                t.printStackTrace();
                fail();
            }
        }
    }
}
