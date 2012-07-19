/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package tmp.texugo.server.httpparser;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that the parser can resume when it is given partial input
 *
 * @author Stuart Douglas
 */
public class ParserResumeTestCase {

    public static final String DATA = "POST /apath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\nHostee:another\r\nAccept-garbage:   a\r\n\r\ntttt";

    @Test
    public void testMethodSplit() {
        byte[] in = DATA.getBytes();
        for(int i = 0; i < in.length - 4; ++i) {
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
        final TokenState context = new TokenState();
        HttpExchangeBuilder result = new HttpExchangeBuilder();
        ByteBuffer buffer = ByteBuffer.wrap(in);
        while (context.state != TokenState.PARSE_COMPLETE){
            HttpParser.INSTANCE.handle(buffer, 1, context, result);
        }
        runAssertions(result, context);
    }

    private void testResume(final int split, byte[] in) {
        final TokenState context = new TokenState();
        HttpExchangeBuilder result = new HttpExchangeBuilder();
        ByteBuffer buffer = ByteBuffer.wrap(in);
        int left = HttpParser.INSTANCE.handle(buffer, split, context, result);
        Assert.assertEquals(0, left);
        left = HttpParser.INSTANCE.handle(buffer, in.length - split, context, result);
        runAssertions(result, context);
        Assert.assertEquals(4, left);
    }

    private void runAssertions(final HttpExchangeBuilder result, final TokenState context) {
        Assert.assertSame("POST", result.method);
        Assert.assertEquals("/apath", result.path);
        Assert.assertSame("HTTP/1.1", result.protocol);
        Assert.assertEquals("www.somehost.net", result.standardHeaders.get("Host"));
        Assert.assertEquals("some value", result.otherHeaders.get("OtherHeader"));
        Assert.assertEquals("a", result.otherHeaders.get("Accept-garbage"));
        Assert.assertEquals(TokenState.PARSE_COMPLETE, context.state);
    }

}
