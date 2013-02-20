/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.client;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.blocking.BlockingHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import junit.framework.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @author Emanuel Muckenhuber
 */
@RunWith(DefaultServer.class)
public class HttpClientTestCase {

    private static final String message = "Hello World!";
    private static XnioWorker worker;

    private static final OptionMap DEFAULT_OPTIONS;
    private static final HttpHandler SIMPLE_MESSAGE_HANDLER;
    private static final SocketAddress ADDRESS = new InetSocketAddress(DefaultServer.getHostPort("default"));
    static {
        final OptionMap.Builder builder = OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true)
                .set(Options.WORKER_NAME, "Client")
                ;

        DEFAULT_OPTIONS = builder.getMap();

        SIMPLE_MESSAGE_HANDLER = new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) {
                sendMessage(exchange);
            }
        };
    }

    static void sendMessage(final HttpServerExchange exchange) {
        exchange.setResponseCode(200);
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length() + "");
        final Sender sender = exchange.getResponseSender();
        sender.send(message, IoCallback.END_EXCHANGE);
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        // Create xnio worker
        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker = xnio.createWorker(null, DEFAULT_OPTIONS);
        worker = xnioWorker;
    }

    @AfterClass
    public static void afterClass() {
        worker.shutdown();
    }

    static HttpClient createClient() {
        return createClient(OptionMap.EMPTY);
    }

    static HttpClient createClient(final OptionMap options) {
        return HttpClient.create(worker, options);
    }

    @Test
    public void testSimpleBasic() throws Exception {
        //
        DefaultServer.setRootHandler(SIMPLE_MESSAGE_HANDLER);
        final HttpClient client = createClient();
        try {
            final HttpClientConnection connection = client.connect(ADDRESS, OptionMap.EMPTY).get();
            try {
                final List<IoFuture<HttpClientResponse>> responses = new ArrayList<IoFuture<HttpClientResponse>>();
                for(int i = 0; i < 10; i++) {
                    final HttpClientRequest request = connection.sendRequest(Methods.GET.toString(), new URI("/"));
                    responses.add(request.writeRequest());
                }
                Assert.assertEquals(10, responses.size());
                for(final IoFuture<HttpClientResponse> future : responses) {
                    HttpClientResponse response = future.get();
                    final StreamSourceChannel channel = response.readReplyBody();
                    try {
                        final InputStream is = new ChannelInputStream(channel);
                        Assert.assertEquals(message, HttpClientUtils.readResponse(is));
                    } finally {
                        IoUtils.safeClose(channel);
                    }
                }
            } finally {
                IoUtils.safeClose(connection);
            }
        } finally {
            IoUtils.safeClose(client);
        }
    }

    @Test
    public void testConnectionClose() throws Exception {
        //
        DefaultServer.setRootHandler(SIMPLE_MESSAGE_HANDLER);
        final HttpClient client = createClient();
        try {
            final HttpClientConnection connection = client.connect(ADDRESS, OptionMap.EMPTY).get();
            try {
                final HttpClientRequest request = connection.sendRequest(Methods.GET.toString(), new URI("/1324"));
                request.getRequestHeaders().add(Headers.CONNECTION, Headers.CLOSE.toString());
                final HttpClientResponse response = request.writeRequest().get();
                final StreamSourceChannel channel = response.readReplyBody();
                try {
                    final InputStream is = new ChannelInputStream(channel);
                    Assert.assertEquals(message, HttpClientUtils.readResponse(is));
                } finally {
                    IoUtils.safeClose(channel);
                }
                try {
                    request.writeRequest();
                    Assert.fail();
                } catch (IOException e) {
                    // OK
                }
            } finally {
                IoUtils.safeClose(connection);
            }
        } finally {
            IoUtils.safeClose(client);
        }
    }

}
