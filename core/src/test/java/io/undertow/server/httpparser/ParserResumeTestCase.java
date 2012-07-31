/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package io.undertow.server.httpparser;

import java.nio.ByteBuffer;

import io.undertow.util.HeaderMap;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that the parser can resume when it is given partial input
 *
 * @author Stuart Douglas
 */
public class ParserResumeTestCase {

    public static final String DATA = "POST http://www.somehost.net/apath?key1=value1&key2=value2 HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\nHostee:another\r\nAccept-garbage:   a\r\n\r\ntttt";

    @Test
    public void testMethodSplit() {
        byte[] in = DATA.getBytes();
        for (int i = 0; i < in.length - 4; ++i) {
            try {
                testResume(i, in);
            } catch (Throwable e) {
                throw new RuntimeException("Test failed at split " + i, e);
            }
        }
    }

    @Test
    public void testOneCharacterAtATime() {
        byte[] in = DATA.getBytes();
        final ParseState context = new ParseState();
        HttpExchangeBuilder result = new HttpExchangeBuilder();
        ByteBuffer buffer = ByteBuffer.wrap(in);
        while (context.state != ParseState.PARSE_COMPLETE) {
            HttpParser.INSTANCE.handle(buffer, 1, context, result);
        }
        runAssertions(result, context);
    }

    private void testResume(final int split, byte[] in) {
        final ParseState context = new ParseState();
        HttpExchangeBuilder result = new HttpExchangeBuilder();
        ByteBuffer buffer = ByteBuffer.wrap(in);
        int left = HttpParser.INSTANCE.handle(buffer, split, context, result);
        Assert.assertEquals(0, left);
        left = HttpParser.INSTANCE.handle(buffer, in.length - split, context, result);
        runAssertions(result, context);
        Assert.assertEquals(4, left);
    }

    private void runAssertions(final HttpExchangeBuilder result, final ParseState context) {
        Assert.assertSame("POST", result.method);
        Assert.assertEquals("/apath?key1=value1&key2=value2", result.canonicalPath);
        Assert.assertEquals("http://www.somehost.net/apath?key1=value1&key2=value2", result.path);
        Assert.assertSame("HTTP/1.1", result.protocol);
        HeaderMap map = new HeaderMap();
        map.add("Host", "www.somehost.net");
        map.add("OtherHeader", "some value");
        map.add("Hostee", "another");
        map.add("Accept-garbage", "a");
        Assert.assertEquals(map, result.headers);

        Assert.assertEquals(ParseState.PARSE_COMPLETE, context.state);
        Assert.assertEquals("value1", result.queryParameters.get("key1").get(0));
        Assert.assertEquals("value2", result.queryParameters.get("key2").get(0));
    }

}
