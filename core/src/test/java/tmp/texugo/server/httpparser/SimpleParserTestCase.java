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
 * @author Stuart Douglas
 */

public class SimpleParserTestCase {

    public static final String[] VERBS = { "GET", "POST", "PUT", };

    public static final String[] VERSIONS = {"HTTP/1.1", "HTTP/1.0"};

    public static final String[] HEADER_VALUES = {
            "Accept",
            "Accept-Charset",
            "Accept-Encoding",
            "Accept-Language",
            "Accept-Ranges",
            "Age",
            "Allow",
            "Authorization",
            "Cache-Control",
            "Cookie",
            "Connection",
            "Content-Disposition",
            "Content-Encoding",
            "Content-Language",
            "Content-Length",
            "Content-Location",
            "Content-MD5",
            "Content-Range",
            "Content-Type",
            "Date",
            "ETag",
            "Expect",
            "Expires",
            "From",
            "Host",
            "If-Match",
            "If-Modified-Since",
            "If-None-Match",
            "If-Range",
            "If-Unmodified-Since",
            "Last-Modified",
            "Location",
            "Max-Forwards",
            "Pragma",
            "Proxy-Authenticate",
            "Proxy-Authorization",
            "Range",
            "Referer",
            "Refresh",
            "Retry-After",
            "Server",
            "Set-Cookie",
            "Set-Cookie2",
            "Strict-Transport-Security",
            "TE",
            "Trailer",
            "Transfer-Encoding",
            "Upgrade",
            "User-Agent",
            "Vary",
            "Via",
            "Warning",
            "WWW-Authenticate"};

    @Test
    public void test() {

        byte[] in = "GET /somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        final TokenState context = new TokenState();
        HttpExchangeBuilder result = new HttpExchangeBuilder();
        HttpParser.INSTANCE.handle(ByteBuffer.wrap(in), in.length, context, result);
        Assert.assertSame("GET", result.verb);
        Assert.assertEquals("/somepath", result.path);
        Assert.assertSame("HTTP/1.1", result.httpVersion);
        Assert.assertEquals("www.somehost.net", result.standardHeaders.get("Host"));
        Assert.assertEquals("some value", result.otherHeaders.get("OtherHeader"));
    }


}
