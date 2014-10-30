/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

package io.undertow.server.handlers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Headers;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.CoreProtocolPNames;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

/**
 * Test that uses a fake channel that returns 0 a lot, to make sure that resume writes works correclty
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ResumeWritesTestCase {


    public static final String HELLO_WORLD = "Hello World";

    @Test
    public void testResumeWritesFixedLength() throws IOException {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.addResponseWrapper(new ReturnZeroWrapper());
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, HELLO_WORLD.length());
                exchange.getResponseSender().send(HELLO_WORLD);
            }
        });

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testResumeWritesChunked() throws IOException {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.addResponseWrapper(new ReturnZeroWrapper());
                exchange.getResponseSender().send(HELLO_WORLD);
            }
        });

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }



    @Test
    public void testResumeWritesHttp10() throws IOException {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.addResponseWrapper(new ReturnZeroWrapper());
                exchange.getResponseSender().send(HELLO_WORLD);
            }
        });

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    private static class ReturnZeroWrapper implements ConduitWrapper<StreamSinkConduit> {

        @Override
        public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
            return new AbstractStreamSinkConduit<StreamSinkConduit>(factory.create()) {

                int c = 0;

                @Override
                public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
                    if(c++ % 100 != 90) return 0;
                    return super.transferFrom(src, position, count);
                }

                @Override
                public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
                    if(c++ % 100 != 90) return 0;
                    return super.transferFrom(source, count, throughBuffer);
                }

                @Override
                public int write(final ByteBuffer src) throws IOException {
                    if(c++ % 100 != 90) return 0;
                    return super.write(src);
                }

                @Override
                public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
                    if(c++ % 100 != 90) return 0;
                    return super.write(srcs, offs, len);
                }

                @Override
                public boolean flush() throws IOException {
                    if(c++ % 100 != 90) return false;
                    return super.flush();
                }
            };
        }
    }
}
