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

package io.undertow.server.protocol.ajp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.xnio.IoUtils;

import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.category.UnitTest;
import io.undertow.util.BadRequestException;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;

/**
 * @author Stuart Douglas
 */
@Category(UnitTest.class)
public class AjpParsingUnitTestCase {

    private static final ByteBuffer buffer;

    static {
        final InputStream stream = AjpParsingUnitTestCase.class.getResourceAsStream("sample-ajp-request");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int r = 0;
            byte[] buf = new byte[1024];
            while ((r = stream.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
            buffer = ByteBuffer.wrap(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IoUtils.safeClose(stream);
        }
    }

    public static final AjpRequestParser AJP_REQUEST_PARSER = new AjpRequestParser("UTF-8", true, 100, 100, false, true);


    @Test
    public void testAjpParsing() throws IOException, BadRequestException {
        final ByteBuffer buffer = AjpParsingUnitTestCase.buffer.duplicate();
        HttpServerExchange result = new HttpServerExchange(null);
        final AjpRequestParseState state = new AjpRequestParseState();
        AJP_REQUEST_PARSER.parse(buffer, state, result);
        Assert.assertEquals(165, state.dataSize);
        Assert.assertTrue(state.isComplete());
        Assert.assertEquals(0, buffer.remaining());
        testResult(result);

    }

    @Test
    public void testByteByByteAjpParsing() throws IOException, BadRequestException {
        final ByteBuffer buffer = AjpParsingUnitTestCase.buffer.duplicate();

        HttpServerExchange result = new HttpServerExchange(null);
        final AjpRequestParseState state = new AjpRequestParseState();
        int limit = buffer.limit();
        for (int i = 1; i <= limit; ++i) {
            buffer.limit(i);
            AJP_REQUEST_PARSER.parse(buffer, state, result);
        }
        Assert.assertEquals(165, state.dataSize);
        Assert.assertTrue(state.isComplete());
        testResult(result);

    }

    private void testResult(final HttpServerExchange exchange) {
        Assert.assertSame(Methods.GET, exchange.getRequestMethod());
        Assert.assertEquals(Protocols.HTTP_1_1, exchange.getProtocol());
        Assert.assertEquals(3, exchange.getRequestHeaders().getHeaderNames().size());
        Assert.assertEquals("localhost:7777", exchange.getRequestHeaders().getFirst(Headers.HOST));
        Assert.assertEquals("Apache-HttpClient/4.1.3 (java 1.5)", exchange.getRequestHeaders().getFirst(Headers.USER_AGENT));
        Assert.assertEquals("Keep-Alive", exchange.getRequestHeaders().getFirst(Headers.CONNECTION));
    }


    @Test
    public void testCharsetHandling() throws Exception {
        ByteBuffer data = createAjpRequest("/hi".getBytes(StandardCharsets.UTF_8));
        HttpServerExchange result = new HttpServerExchange(null);
        AjpRequestParseState state = new AjpRequestParseState();
        AJP_REQUEST_PARSER.parse(data, state, result);
        Assert.assertEquals("/hi", result.getRequestPath());
        Assert.assertEquals("/hi", result.getRequestURI());

        data = createAjpRequest("/한글이름".getBytes(StandardCharsets.UTF_8));
        result = new HttpServerExchange(null);
        state = new AjpRequestParseState();
        AJP_REQUEST_PARSER.parse(data, state, result);
        Assert.assertEquals("/한글이름", result.getRequestPath());
        Assert.assertEquals("/한글이름", result.getRequestURI());

    }

    protected ByteBuffer createAjpRequest(byte[] path) {
        ByteBuffer data = ByteBuffer.allocate(1000);
        data.put((byte) 0x12);
        data.put((byte) 0x34);
        data.put((byte) 0); //size
        data.put((byte) 0);
        data.put((byte) 2);
        data.put((byte) 2); //GET method
        putString(data, "HTTP/1.1");
        putString(data, path);
        putString(data, "");//REMOTE_ADDRESS
        putString(data, "");//REMOTE_HOST
        putString(data, "");//SERVER_NAME
        putInt(data, 100); //SERVER_PORT
        data.put((byte) 0); //IS_SSL
        putInt(data, 0); //number of headers
        data.put((byte) 0xFF);
        int dataLength = data.position() - 4;
        data.put(2, (byte) ((dataLength >> 8) & 0xFF));
        data.put(3, (byte) (dataLength & 0xFF));
        data.flip();
        return data;
    }


    static void putInt(final ByteBuffer buf, int value) {
        buf.put((byte) ((value >> 8) & 0xFF));
        buf.put((byte) (value & 0xFF));
    }

    static void putString(final ByteBuffer buf, String value) {
        final int length = value.length();
        putInt(buf, length);
        for (int i = 0; i < length; ++i) {
            buf.put((byte) value.charAt(i));
        }
        buf.put((byte) 0);
    }
    static void putString(final ByteBuffer buf, byte[] value) {
        final int length = value.length;
        putInt(buf, length);
        for (int i = 0; i < length; ++i) {
            buf.put(value[i]);
        }
        buf.put((byte) 0);
    }
}
