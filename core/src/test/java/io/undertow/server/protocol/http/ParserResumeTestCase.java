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

package io.undertow.server.protocol.http;

import java.nio.ByteBuffer;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import org.junit.Assert;
import org.junit.Test;
import org.xnio.OptionMap;

/**
 * Tests that the parser can resume when it is given partial input
 *
 * @author Stuart Douglas
 */
public class ParserResumeTestCase {

    public static final String DATA = "GET http://www.somehost.net/apath+with+spaces%20and%20I%C3%B1t%C3%ABrn%C3%A2ti%C3%B4n%C3%A0li%C5%BE%C3%A6ti%C3%B8n?key1=value1&key2=I%C3%B1t%C3%ABrn%C3%A2ti%C3%B4n%C3%A0li%C5%BE%C3%A6ti%C3%B8n HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\nHostee:another\r\nAccept-garbage:   a\r\n\r\ntttt";
    public static final HttpRequestParser PARSER = HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true));

    final ParseState context = new ParseState();
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
        context.reset();
        byte[] in = DATA.getBytes();
        HttpServerExchange result = new HttpServerExchange(null);
        ByteBuffer buffer = ByteBuffer.wrap(in);
        int oldLimit = buffer.limit();
        buffer.limit(1);
        while (context.state != ParseState.PARSE_COMPLETE) {
            PARSER.handle(buffer, context, result);
            if(context.state != ParseState.PARSE_COMPLETE) {
                buffer.limit(buffer.limit() + 1);
            }
        }
        Assert.assertEquals(oldLimit, buffer.limit() + 4);
        runAssertions(result);
    }

    private void testResume(final int split, byte[] in) {
        context.reset();
        HttpServerExchange result = new HttpServerExchange(null);
        ByteBuffer buffer = ByteBuffer.wrap(in);
        buffer.limit(split);
        PARSER.handle(buffer, context, result);
        buffer.limit(buffer.capacity());
        PARSER.handle(buffer, context, result);
        runAssertions(result);
        Assert.assertEquals(4, buffer.remaining());
    }

    private void runAssertions(final HttpServerExchange result) {
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/apath with spaces and Iñtërnâtiônàližætiøn", result.getRelativePath());
        Assert.assertEquals("http://www.somehost.net/apath+with+spaces%20and%20I%C3%B1t%C3%ABrn%C3%A2ti%C3%B4n%C3%A0li%C5%BE%C3%A6ti%C3%B8n", result.getRequestURI());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());

        Assert.assertEquals("www.somehost.net", result.getRequestHeaders().getFirst(new HttpString("Host")));
        Assert.assertEquals("some value", result.getRequestHeaders().getFirst(new HttpString("OtherHeader")));
        Assert.assertEquals("another", result.getRequestHeaders().getFirst(new HttpString("Hostee")));
        Assert.assertEquals("a", result.getRequestHeaders().getFirst(new HttpString("Accept-garbage")));
        Assert.assertEquals(4, result.getRequestHeaders().getHeaderNames().size());

        Assert.assertEquals(ParseState.PARSE_COMPLETE, context.state);
        Assert.assertEquals("key1=value1&key2=I%C3%B1t%C3%ABrn%C3%A2ti%C3%B4n%C3%A0li%C5%BE%C3%A6ti%C3%B8n", result.getQueryString());
        Assert.assertEquals("value1", result.getQueryParameters().get("key1").getFirst());
        Assert.assertEquals("Iñtërnâtiônàližætiøn", result.getQueryParameters().get("key2").getFirst());
    }

}
