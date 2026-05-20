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

package io.undertow.client.http;

import io.undertow.testutils.category.UnitTest;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.ByteBuffer;

/**
 * Tests that the parser can resume when it is given partial input
 *
 * @author Stuart Douglas
 */
@Category(UnitTest.class)
public class ResponseParserResumeTestCase {

    public static final String RESPONSE1 = "HTTP/1.1 200 OK\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n \r\n value\r\nHostee:another\r\nAccept-garbage:   a\r\n\r\ntttt";
    public static final String RESPONSE2 = "HTTP/1.1 200 \r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n \r\n value\r\nHostee:another\r\nAccept-garbage:   a\r\n\r\ntttt";
    public static final String RESPONSE3 = "HTTP/1.1 200 \r\n\r\ntttt";

    @Test
    public void testMethodSplit_response1() {
        byte[] in = RESPONSE1.getBytes();
        for (int i = 0; i < in.length - 4; ++i) {
            try {
                testResume(i, in, true, true);
            } catch (Throwable e) {
                throw new RuntimeException("Test failed at split " + i, e);
            }
        }
    }

    @Test
    public void testOneCharacterAtATime_response1() {
        byte[] in = RESPONSE1.getBytes();
        final ResponseState context = new ResponseState();
        HttpResponseBuilder result = new HttpResponseBuilder();
        ByteBuffer buffer = ByteBuffer.wrap(in);
        buffer.limit(1);
        while (!context.isComplete()) {
            ResponseParser.INSTANCE.handle(buffer, context, result);
            buffer.limit(buffer.limit() + 1);
        }
        runAssertions(result, context, true, true);
    }

    @Test
    public void testOneCharacterAtATime_response2() {
        byte[] in = RESPONSE2.getBytes();
        final ResponseState context = new ResponseState();
        HttpResponseBuilder result = new HttpResponseBuilder();
        ByteBuffer buffer = ByteBuffer.wrap(in);
        buffer.limit(1);
        while (!context.isComplete()) {
            ResponseParser.INSTANCE.handle(buffer, context, result);
            buffer.limit(buffer.limit() + 1);
        }
        runAssertions(result, context, false, true);
    }

    @Test
    public void testOneCharacterAtATime_response3() {
        byte[] in = RESPONSE3.getBytes();
        final ResponseState context = new ResponseState();
        HttpResponseBuilder result = new HttpResponseBuilder();
        ByteBuffer buffer = ByteBuffer.wrap(in);
        buffer.limit(1);
        while (!context.isComplete()) {
            ResponseParser.INSTANCE.handle(buffer, context, result);
            buffer.limit(buffer.limit() + 1);
        }
        runAssertions(result, context, false, false);
    }

    private void testResume(final int split, byte[] in, final boolean hasReasonPhrase, final boolean hasHeaders) {
        final ResponseState context = new ResponseState();
        HttpResponseBuilder result = new HttpResponseBuilder();
        ByteBuffer buffer = ByteBuffer.wrap(in);
        buffer.limit(split);
        ResponseParser.INSTANCE.handle(buffer, context, result);
        Assert.assertEquals(0, buffer.remaining());
        buffer.limit(buffer.capacity());
        ResponseParser.INSTANCE.handle(buffer,context, result);
        runAssertions(result, context, hasReasonPhrase, hasHeaders);
        Assert.assertEquals(4, buffer.remaining());
    }

    private void runAssertions(final HttpResponseBuilder result, final ResponseState context, final boolean hasReasonPhrase, final boolean hasHeaders) {
        Assert.assertEquals(StatusCodes.OK, result.getStatusCode());
        Assert.assertEquals(hasReasonPhrase ? "OK" : null, result.getReasonPhrase());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());

        if (hasHeaders) {
            Assert.assertEquals("www.somehost.net", result.getResponseHeaders().getFirst(new HttpString("Host")));
            Assert.assertEquals("some value", result.getResponseHeaders().getFirst(new HttpString("OtherHeader")));
            Assert.assertEquals("another", result.getResponseHeaders().getFirst(new HttpString("Hostee")));
            Assert.assertEquals("a", result.getResponseHeaders().getFirst(new HttpString("Accept-garbage")));
            Assert.assertEquals(4, result.getResponseHeaders().getHeaderNames().size());
        } else {
            Assert.assertEquals(0, result.getResponseHeaders().getHeaderNames().size());
        }

        Assert.assertTrue(context.isComplete());
    }

}
