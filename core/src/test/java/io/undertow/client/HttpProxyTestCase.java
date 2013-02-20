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
import io.undertow.server.handlers.PathHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import junit.framework.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Emanuel Muckenhuber
 */
@RunWith(DefaultServer.class)
public class HttpProxyTestCase {

    private static final String MESSAGE = "hello world!";
    private static XnioWorker worker;

    private static final OptionMap DEFAULT_OPTIONS;
    static {
        final OptionMap.Builder builder = OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true)
                .set(Options.WORKER_NAME, "Client")
                ;

        DEFAULT_OPTIONS = builder.getMap();
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        // Create xnio worker
        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker = xnio.createWorker(null, DEFAULT_OPTIONS);
        worker = xnioWorker;

        // proxy request from /rewrite to /real
        final PathHandler paths = new PathHandler();
        paths.addPath("/rewrite", new HttpProxyHandler(worker, OptionMap.EMPTY));
        paths.addPath("/real", new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) {
                final StreamSourceChannel channel = exchange.getRequestChannel();
                try {
                    final InputStream is = new ChannelInputStream(channel);
                    final String message = HttpClientUtils.readResponse(is);
                    exchange.setResponseCode(200);
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length() + "");
                    final Sender sender = exchange.getResponseSender();
                    sender.send(message, IoCallback.END_EXCHANGE);
                } catch(IOException e) {
                    exchange.setResponseCode(500);
                    exchange.endExchange();
                }
            }
        });
        DefaultServer.setRootHandler(paths);
    }

    @Test
    public void testSimpleEchoProxy() throws Exception {

        final SocketAddress address = new InetSocketAddress(DefaultServer.getHostPort("default"));
        final HttpClient client = HttpClient.create(worker, OptionMap.EMPTY);
        try {
            final HttpClientConnection connection = client.connect(address, OptionMap.EMPTY).get();
            try {
                for(int i = 0; i < 10; i++) {
                    final String message = MESSAGE;
                    final HttpClientRequest request = connection.sendRequest(Methods.POST.toString(), new URI("/rewrite"));
                    final StreamSinkChannel requestChannel = request.writeRequestBody(message.length());
                    try {
                        final ChannelOutputStream os = new ChannelOutputStream(requestChannel);
                        os.write(message.getBytes());
                        os.flush();
                    } finally {
                        IoUtils.safeClose(requestChannel);
                    }

                    final HttpClientResponse response = request.getResponse().get();
                    final StreamSourceChannel responseChannel = response.readReplyBody();
                    try {
                        final InputStream is = new ChannelInputStream(responseChannel);
                        Assert.assertEquals(message, HttpClientUtils.readResponse(is));
                    } finally {
                        IoUtils.safeClose(responseChannel);
                    }
                }
            } finally {
                IoUtils.safeClose(connection);
            }
        } finally {
            IoUtils.safeClose(client);
        }
    }

}
