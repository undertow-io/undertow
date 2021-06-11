/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.push;

import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.PushCallback;
import io.undertow.client.UndertowClient;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.OpenListener;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.protocol.http.AlpnOpenListener;
import io.undertow.server.protocol.http2.Http2OpenListener;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.SingleByteStreamSinkConduit;
import io.undertow.util.SingleByteStreamSourceConduit;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * <p>Test that checks that push promises are returned and double promises
 * are avoid (a resource sent as a promise trigers another promise).</p>
 *
 * @author rmartinc
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class PushPromisesTestCase {

    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);

    private static OpenListener openListener;
    private static ChannelListener acceptListener;
    private static XnioWorker worker;

    private static ChannelListener<StreamConnection> wrapOpenListener(final ChannelListener<StreamConnection> listener) {
        return (StreamConnection channel) -> {
            channel.getSinkChannel().setConduit(new SingleByteStreamSinkConduit(channel.getSinkChannel().getConduit(), 10000));
            channel.getSourceChannel().setConduit(new SingleByteStreamSourceConduit(channel.getSourceChannel().getConduit(), 10000));
            listener.handleEvent(channel);
        };
    }

    @BeforeClass
    public static void setup() throws Exception {
        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        ServletInfo s = new ServletInfo("servlet", PushServlet.class)
                .addMappings("/index.html", "/resources/*");
        DeploymentInfo info = new DeploymentInfo()
                .setClassLoader(PushPromisesTestCase.class.getClassLoader())
                .setContextPath("/push-example")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("push-example.war")
                .addServlet(s);

        DeploymentManager manager = container.addDeployment(info);
        manager.deploy();
        root.addPrefixPath(info.getContextPath(), manager.start());

        openListener = new Http2OpenListener(DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true, UndertowOptions.HTTP2_PADDING_SIZE, 10));
        acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(new AlpnOpenListener(DefaultServer.getBufferPool()).addProtocol(Http2OpenListener.HTTP2, (io.undertow.server.DelegateOpenListener) openListener, 10)));
        openListener.setRootHandler(root);

        DefaultServer.startSSLServer(OptionMap.EMPTY, acceptListener);

        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker = xnio.createWorker(null,
                OptionMap.builder()
                        .set(Options.WORKER_IO_THREADS, 8)
                        .set(Options.TCP_NODELAY, true)
                        .set(Options.KEEP_ALIVE, true)
                        .set(Options.WORKER_NAME, "Client").getMap());
        worker = xnioWorker;
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        openListener.closeConnections();
        DefaultServer.stopSSLServer();
    }

    private PushCallback createPushCallback(final Map<String, ClientResponse> responses, final CountDownLatch latch) {
        return new PushCallback() {
            @Override
            public boolean handlePush(ClientExchange originalRequest, ClientExchange pushedRequest) {
                pushedRequest.setResponseListener(new ResponseListener(responses, latch));
                return true;
            }
        };
    }

    private ClientCallback<ClientExchange> createClientCallback(final Map<String, ClientResponse> responses, final CountDownLatch latch) {
        return new ClientCallback<ClientExchange>() {
            @Override
            public void completed(final ClientExchange result) {
                result.setResponseListener(new ResponseListener(responses, latch));
                result.setPushHandler(createPushCallback(responses, latch));
            }

            @Override
            public void failed(IOException e) {
                e.printStackTrace();
                latch.countDown();
            }
        };
    }

    private static class ResponseListener implements ClientCallback<ClientExchange> {

        private final Map<String, ClientResponse> responses;
        private final CountDownLatch latch;

        ResponseListener(Map<String, ClientResponse> responses, CountDownLatch latch) {
            this.responses = responses;
            this.latch = latch;
        }

        @Override
        public void completed(final ClientExchange result) {
            responses.put(result.getRequest().getPath(), result.getResponse());
            new StringReadChannelListener(result.getConnection().getBufferPool()) {

                @Override
                protected void stringDone(String string) {
                    result.getResponse().putAttachment(RESPONSE_BODY, string);
                    latch.countDown();
                }

                @Override
                protected void error(IOException e) {
                    e.printStackTrace();
                    latch.countDown();
                }
            }.setup(result.getResponseChannel());
        }

        @Override
        public void failed(IOException e) {
            e.printStackTrace();
            latch.countDown();
        }
    }

    @Test
    public void testPushPromises() throws Exception {
        URI uri = new URI(DefaultServer.getDefaultServerSSLAddress());
        final UndertowClient client = UndertowClient.getInstance();
        final Map<String, ClientResponse> responses = new ConcurrentHashMap<>();
        final CountDownLatch latch = new CountDownLatch(3);
        final ClientConnection connection = client.connect(uri, worker, new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext()), DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true))
                .get();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath("/push-example/index.html");
                    request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                    connection.sendRequest(request, createClientCallback(responses, latch));
                }
            });
            latch.await(10, TimeUnit.SECONDS);
            Assert.assertEquals(3, responses.size());
            Assert.assertTrue(responses.containsKey("/push-example/index.html"));
            Assert.assertEquals(StatusCodes.OK, responses.get("/push-example/index.html").getResponseCode());
            Assert.assertNotNull(responses.get("/push-example/index.html").getAttachment(RESPONSE_BODY));
            Assert.assertTrue(responses.containsKey("/push-example/resources/one.js"));
            Assert.assertEquals(StatusCodes.OK, responses.get("/push-example/resources/one.js").getResponseCode());
            Assert.assertNotNull(responses.get("/push-example/resources/one.js").getAttachment(RESPONSE_BODY));
            Assert.assertTrue(responses.containsKey("/push-example/resources/one.css"));
            Assert.assertEquals(StatusCodes.OK, responses.get("/push-example/resources/one.css").getResponseCode());
            Assert.assertNotNull(responses.get("/push-example/resources/one.css").getAttachment(RESPONSE_BODY));
        } finally {
            IoUtils.safeClose(connection);
        }
    }
}
