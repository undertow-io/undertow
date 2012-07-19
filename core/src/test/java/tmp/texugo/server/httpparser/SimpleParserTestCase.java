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

package tmp.texugo.server.httpparser;

import java.nio.ByteBuffer;

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

    private void runTest(final byte[] in) {
        final TokenState context = new TokenState();
        HttpExchangeBuilder result = new HttpExchangeBuilder();
        HttpParser.INSTANCE.handle(ByteBuffer.wrap(in), in.length, context, result);
        Assert.assertSame("GET", result.method);
        Assert.assertEquals("/somepath", result.path);
        Assert.assertSame("HTTP/1.1", result.protocol);
        Assert.assertEquals("www.somehost.net", result.standardHeaders.get("Host"));
        Assert.assertEquals("some value", result.otherHeaders.get("OtherHeader"));
        Assert.assertEquals(TokenState.PARSE_COMPLETE, context.state);
    }


}
