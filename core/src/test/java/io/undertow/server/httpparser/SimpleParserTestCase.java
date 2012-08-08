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
 * Basic test of the HTTP parser functionality.
 *
 * This tests parsing the same basic request, over and over, with minor differences.
 *
 * Not all these actually conform to the HTTP/1.1 specification, however we are supposed to be
 * liberal in what we accept.
 *
 * @author Stuart Douglas
 */
public class SimpleParserTestCase {


    @Test
    public void testSimpleRequest() {
        byte[] in = "GET /somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test
    public void testCarriageReturnLineEnds() {

        byte[] in = "GET /somepath HTTP/1.1\rHost:   www.somehost.net\rOtherHeader: some\r    value\r\r\n".getBytes();
        runTest(in);
    }

    @Test
    public void testLineFeedsLineEnds() {
        byte[] in = "GET /somepath HTTP/1.1\nHost:   www.somehost.net\nOtherHeader: some\n    value\n\r\n".getBytes();
        runTest(in);
    }

    @Test
    public void testTabWhitespace() {
        byte[] in = "GET\t/somepath\tHTTP/1.1\nHost: \t www.somehost.net\nOtherHeader:\tsome\n \t  value\n\r\n".getBytes();
        runTest(in);
    }

    @Test
    public void testCanonicalPath() {
        byte[] in = "GET\thttp://www.somehost.net/somepath\tHTTP/1.1\nHost: \t www.somehost.net\nOtherHeader:\tsome\n \t  value\n\r\n".getBytes();

        final ParseState context = new ParseState();
        HttpExchangeBuilder result = new HttpExchangeBuilder();
        HttpParser.INSTANCE.handle(ByteBuffer.wrap(in), in.length, context, result);
        Assert.assertEquals("/somepath", result.relativePath);
        Assert.assertEquals("http://www.somehost.net/somepath", result.fullPath);
    }

    @Test
    public void testQueryParams() {
        byte[] in = "GET\thttp://www.somehost.net/somepath?a=b&b=c&d&e&f=\tHTTP/1.1\nHost: \t www.somehost.net\nOtherHeader:\tsome\n \t  value\n\r\n".getBytes();

        final ParseState context = new ParseState();
        HttpExchangeBuilder result = new HttpExchangeBuilder();
        HttpParser.INSTANCE.handle(ByteBuffer.wrap(in), in.length, context, result);
        Assert.assertEquals("/somepath?a=b&b=c&d&e&f=", result.relativePath);
        Assert.assertEquals("http://www.somehost.net/somepath?a=b&b=c&d&e&f=", result.fullPath);
        Assert.assertEquals("b", result.queryParameters.get("a").get(0));
        Assert.assertEquals("c", result.queryParameters.get("b").get(0));
        Assert.assertEquals("", result.queryParameters.get("d").get(0));
        Assert.assertEquals("", result.queryParameters.get("e").get(0));
        Assert.assertEquals("", result.queryParameters.get("f").get(0));

    }

    private void runTest(final byte[] in) {
        final ParseState context = new ParseState();
        HttpExchangeBuilder result = new HttpExchangeBuilder();
        HttpParser.INSTANCE.handle(ByteBuffer.wrap(in), in.length, context, result);
        Assert.assertSame("GET", result.method);
        Assert.assertEquals("/somepath", result.fullPath);
        Assert.assertSame("HTTP/1.1", result.protocol);
        HeaderMap map = new HeaderMap();
        map.add("Host", "www.somehost.net");
        map.add("OtherHeader", "some value");
        Assert.assertEquals(map, result.headers);
        Assert.assertEquals(ParseState.PARSE_COMPLETE, context.state);
    }


}
