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

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.category.UnitTest;
import io.undertow.util.BadRequestException;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.xnio.OptionMap;

/**
 * Basic test of the HTTP parser functionality.
 * <p>
 * This tests parsing the same basic request, over and over, with minor differences.
 * <p>
 *
 * @author Stuart Douglas
 */
@Category(UnitTest.class)
public class SimpleParserTestCase {

    private final ParseState parseState = new ParseState(-1);

    @Test
    public void testEncodedSlashDisallowed() throws BadRequestException {
        byte[] in = "GET /somepath%2FotherPath HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/somepath%2FotherPath", result.getRequestURI());
        Assert.assertEquals("/somepath%2FotherPath", result.getRequestPath());
    }

    @Test
    public void testEncodedSlashAllowed() throws BadRequestException {
        byte[] in = "GET /somepath%2fotherPath HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/somepath/otherPath", result.getRequestPath());
        Assert.assertEquals("/somepath%2fotherPath", result.getRequestURI());
    }

    @Test
    public void testColonSlashInURL() throws BadRequestException {
        byte[] in = "GET /a/http://myurl.com/b/c HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/a/http://myurl.com/b/c", result.getRequestPath());
        Assert.assertEquals("/a/http://myurl.com/b/c", result.getRequestURI());
    }

    @Test
    public void testColonSlashInFullURL() throws BadRequestException {
        byte[] in = "GET http://foo.com/a/http://myurl.com/b/c HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/a/http://myurl.com/b/c", result.getRequestPath());
        Assert.assertEquals("http://foo.com/a/http://myurl.com/b/c", result.getRequestURI());
    }


    @Test
    public void testMatrixParamFlag() throws BadRequestException {
        byte[] in = "GET /somepath;p1 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/somepath;p1", result.getRequestURI());
        Assert.assertEquals("/somepath", result.getRequestPath());
        Assert.assertEquals(1, result.getPathParameters().size());
        Assert.assertEquals("p1", result.getPathParameters().keySet().toArray()[0]);
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testMatrixParamFlagEndingWithNormalPath() throws BadRequestException {
        byte[] in = "GET /somepath;p1/more HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/somepath;p1/more", result.getRequestURI());
        Assert.assertEquals("/somepath/more", result.getRequestPath());
        Assert.assertEquals(1, result.getPathParameters().size());
        Assert.assertEquals("p1", result.getPathParameters().keySet().toArray()[0]);
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testMultipleMatrixParamsOfSameName() throws BadRequestException {
        byte[] in = "GET /somepath;p1=v1;p1=v2 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/somepath;p1=v1;p1=v2", result.getRequestURI());
        Assert.assertEquals("/somepath", result.getRequestPath());
        Assert.assertEquals(1, result.getPathParameters().size());
        Assert.assertEquals("p1", result.getPathParameters().keySet().toArray()[0]);
        Assert.assertEquals("v1", result.getPathParameters().get("p1").getFirst());
        Assert.assertEquals("v2", result.getPathParameters().get("p1").getLast());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testCommaSeparatedParamValues() throws BadRequestException {
        byte[] in = "GET /somepath;p1=v1,v2 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/somepath;p1=v1,v2", result.getRequestURI());
        Assert.assertEquals("/somepath", result.getRequestPath());
        Assert.assertEquals(1, result.getPathParameters().size());
        Assert.assertEquals("p1", result.getPathParameters().keySet().toArray()[0]);
        Assert.assertEquals("v1", result.getPathParameters().get("p1").getFirst());
        Assert.assertEquals("v2", result.getPathParameters().get("p1").getLast());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testServletURLWithPathParam() throws BadRequestException {
        byte[] in = "GET http://localhost:7777/servletContext/aaaa/b;param=1 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("http://localhost:7777/servletContext/aaaa/b;param=1", result.getRequestURI());
        Assert.assertEquals("/servletContext/aaaa/b", result.getRequestPath());
        Assert.assertEquals(1, result.getPathParameters().size());
        Assert.assertEquals("param", result.getPathParameters().keySet().toArray()[0]);
        Assert.assertEquals("1", result.getPathParameters().get("param").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertTrue(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testServletURLWithPathParamEndingWithNormalPath() throws BadRequestException {
        byte[] in = "GET http://localhost:7777/servletContext/aaaa/b;param=1/cccc HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("http://localhost:7777/servletContext/aaaa/b;param=1/cccc", result.getRequestURI());
        Assert.assertEquals("/servletContext/aaaa/b/cccc", result.getRequestPath());
        Assert.assertEquals(1, result.getPathParameters().size());
        Assert.assertEquals("param", result.getPathParameters().keySet().toArray()[0]);
        Assert.assertEquals("1", result.getPathParameters().get("param").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertTrue(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testServletURLWithPathParams() throws BadRequestException {
        byte[] in = "GET http://localhost:7777/servletContext/aa/b;foo=bar;mysessioncookie=mSwrYUX8_e3ukAylNMkg3oMRglB4-YjxqeWqXQsI HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("http://localhost:7777/servletContext/aa/b;foo=bar;mysessioncookie=mSwrYUX8_e3ukAylNMkg3oMRglB4-YjxqeWqXQsI", result.getRequestURI());
        Assert.assertEquals("/servletContext/aa/b", result.getRequestPath());
        Assert.assertEquals(2, result.getPathParameters().size());

        Assert.assertEquals("bar", result.getPathParameters().get("foo").getFirst());
        Assert.assertEquals("mSwrYUX8_e3ukAylNMkg3oMRglB4-YjxqeWqXQsI", result.getPathParameters().get("mysessioncookie").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertTrue(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testServletPathWithPathParam() throws BadRequestException {
        byte[] in = "GET /servletContext/aaaa/b;param=1 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/servletContext/aaaa/b;param=1", result.getRequestURI());
        Assert.assertEquals("/servletContext/aaaa/b", result.getRequestPath());
        Assert.assertEquals(1, result.getPathParameters().size());
        Assert.assertEquals("param", result.getPathParameters().keySet().toArray()[0]);
        Assert.assertEquals("1", result.getPathParameters().get("param").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testServletPathWithPathParams() throws BadRequestException {
        byte[] in = "GET /servletContext/aa/b;foo=bar;mysessioncookie=mSwrYUX8_e3ukAylNMkg3oMRglB4-YjxqeWqXQsI HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/servletContext/aa/b;foo=bar;mysessioncookie=mSwrYUX8_e3ukAylNMkg3oMRglB4-YjxqeWqXQsI", result.getRequestURI());
        Assert.assertEquals("/servletContext/aa/b", result.getRequestPath());
        Assert.assertEquals(2, result.getPathParameters().size());

        Assert.assertEquals("bar", result.getPathParameters().get("foo").getFirst());
        Assert.assertEquals("mSwrYUX8_e3ukAylNMkg3oMRglB4-YjxqeWqXQsI", result.getPathParameters().get("mysessioncookie").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testRootMatrixParam() throws BadRequestException {
        // TODO decide what should happen for a single semicolon as the path URI and other edge cases
        byte[] in = "GET ; HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals(";", result.getRequestURI());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
    }

    @Test
    public void testMatrixParametersWithQueryString() throws BadRequestException {
        byte[] in = "GET /somepath;p1=v1;p2=v2?q1=v3 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/somepath;p1=v1;p2=v2", result.getRequestURI());
        Assert.assertEquals("/somepath", result.getRequestPath());
        //Assert.assertEquals("q1=v3", result.getQueryString());
        Assert.assertEquals("v1", result.getPathParameters().get("p1").getFirst());
        Assert.assertEquals("v2", result.getPathParameters().get("p2").getFirst());

        Assert.assertEquals("q1=v3", result.getQueryString());
        Assert.assertEquals("v3", result.getQueryParameters().get("q1").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testMultiLevelMatrixParameter() throws BadRequestException {
        byte[] in = "GET /some;p1=v1/path;p1=v2?q1=v3 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/some;p1=v1/path;p1=v2", result.getRequestURI());
        Assert.assertEquals("/some/path", result.getRequestPath());
        Assert.assertEquals("q1=v3", result.getQueryString());
        Assert.assertEquals("v1", result.getPathParameters().get("p1").getFirst());
        Assert.assertEquals("v2", result.getPathParameters().get("p1").getLast());
        Assert.assertEquals("v3", result.getQueryParameters().get("q1").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testServletURLMultiLevelMatrixParameter() throws BadRequestException {
        byte[] in = "GET http://localhost:7777/some;p1=v1/path;p1=v2?q1=v3 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("http://localhost:7777/some;p1=v1/path;p1=v2", result.getRequestURI());
        Assert.assertEquals("/some/path", result.getRequestPath());
        Assert.assertEquals("q1=v3", result.getQueryString());
        Assert.assertEquals("v1", result.getPathParameters().get("p1").getFirst());
        Assert.assertEquals("v2", result.getPathParameters().get("p1").getLast());
        Assert.assertEquals("v3", result.getQueryParameters().get("q1").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertTrue(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testMultiLevelMatrixParameters() throws BadRequestException {
        byte[] in = "GET /some;p1=v1/path;p2=v2?q1=v3 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/some;p1=v1/path;p2=v2", result.getRequestURI());
        Assert.assertEquals("/some/path", result.getRequestPath());
        Assert.assertEquals("q1=v3", result.getQueryString());
        Assert.assertEquals("v1", result.getPathParameters().get("p1").getFirst());
        Assert.assertEquals("v2", result.getPathParameters().get("p2").getFirst());
        Assert.assertEquals("v3", result.getQueryParameters().get("q1").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testMultiLevelMatrixParameterEndingWithNormalPathAndQuery() throws BadRequestException {
        byte[] in = "GET /some;p1=v1/path;p1=v2/more?q1=v3 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/some;p1=v1/path;p1=v2/more", result.getRequestURI());
        Assert.assertEquals("/some/path/more", result.getRequestPath());
        Assert.assertEquals("q1=v3", result.getQueryString());
        Assert.assertEquals("v1", result.getPathParameters().get("p1").getFirst());
        Assert.assertEquals("v2", result.getPathParameters().get("p1").getLast());
        Assert.assertEquals("v3", result.getQueryParameters().get("q1").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testServletURLMultiLevelMatrixParameterEndingWithNormalPathAndQuery() throws BadRequestException {
        byte[] in = "GET http://localhost:7777/some;p1=v1/path;p1=v2/more?q1=v3 HTTP/1.1\r\n\r\n".getBytes();
        ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("http://localhost:7777/some;p1=v1/path;p1=v2/more", result.getRequestURI());
        Assert.assertEquals("/some/path/more", result.getRequestPath());
        Assert.assertEquals("q1=v3", result.getQueryString());
        Assert.assertEquals("v1", result.getPathParameters().get("p1").getFirst());
        Assert.assertEquals("v2", result.getPathParameters().get("p1").getLast());
        Assert.assertEquals("v3", result.getQueryParameters().get("q1").getFirst());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());
        Assert.assertTrue(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testFullUrlRootPath() throws BadRequestException {
        byte[] in = "GET http://myurl.com HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/", result.getRequestPath());
        Assert.assertEquals("http://myurl.com", result.getRequestURI());
        Assert.assertTrue(result.isHostIncludedInRequestURI());
    }

    @Test
    public void testHTTPClientRequest() throws BadRequestException {
        byte[] in = ("POST /wildfly-services/ejb/v1/invoke/-/ejb-remote-server-side/-/CalculatorBean/-/org.jboss.as.quickstarts.ejb.remote.stateless.RemoteCalculator/concat/%5BLjava.lang.String%3B HTTP/1.1\r\n" +
        "Accept: application/x-wf-ejb-response;version=1,application/x-wf-jbmar-exception;version=1\r\n" +
        "Authorization: Digest username=\"quickstartUser\", uri=\"http://localhost:8080/wildfly-services/ejb/v1/invoke/-/ejb-remote-server-side/-/CalculatorBean/-/org.jboss.as.quickstarts.ejb.remote.stateless.RemoteCalculator/concat/%255BLjava.lang.String%253B\", realm=\"ApplicationRealm\", nc=00000052, cnonce=\"HgqQmHZKAJC44WA8W3yZvJmxNn9p6tpYE-gXd5BB\", algorithm=MD5, nonce=\"AAAABQABIeNiOHTsLqhR+iBs2wrWA4rOh5kc9QIjztvj4f3106O7qmH3LVQ=\", opaque=\"00000000000000000000000000000000\", qop=auth, response=\"daf568673f3998b7c50a86cfaa5e0d21\"\r\n" +
        "Transfer-Encoding: chunked\r\n" +
        "Content-Type: application/x-wf-ejb-jbmar-invocation;version=1\r\n" +
        "Cookie: JSESSIONID=9ZcvdCLqUtp0hVD3C5XUfH_pczo8bos3qfHfyQDB.localhost\r\n" +
        "Host: localhost:8080\r\n").getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        OptionMap map = OptionMap.builder().set(org.xnio.Options.BALANCING_TOKENS, 1)
                .set(org.xnio.Options.REUSE_ADDRESSES, true)
                .set(io.undertow.UndertowOptions.DECODE_URL, true)
                .set(io.undertow.UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true)
                .set(io.undertow.UndertowOptions.ENABLE_STATISTICS, false)
                .set(io.undertow.UndertowOptions.RECORD_REQUEST_START_TIME, false)
                .set(io.undertow.UndertowOptions.NO_REQUEST_TIMEOUT, 60000)
                .set(io.undertow.UndertowOptions.MAX_HEADER_SIZE, 1048576)
                .set(io.undertow.UndertowOptions.MAX_COOKIES, 200)
                .set(io.undertow.UndertowOptions.MAX_PARAMETERS, 1000)
                .set(io.undertow.UndertowOptions.ALLOW_ENCODED_SLASH, false)
                .set(org.xnio.Options.TCP_NODELAY, true)
                .set(io.undertow.UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, false)
                .set(io.undertow.UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH, true)
                .set(io.undertow.UndertowOptions.HTTP2_SETTINGS_HEADER_TABLE_SIZE, 4096)
                .set(io.undertow.UndertowOptions.URL_CHARSET, "UTF-8")
                .set(org.xnio.Options.BALANCING_CONNECTIONS, 2)
                .set(io.undertow.UndertowOptions.BUFFER_PIPELINED_DATA, false)
                .set(io.undertow.UndertowOptions.MAX_ENTITY_SIZE, 10485760)
                .set(io.undertow.UndertowOptions.MAX_HEADERS, 200)
                .set(io.undertow.UndertowOptions.HTTP2_SETTINGS_INITIAL_WINDOW_SIZE, 65535)
                .set(io.undertow.UndertowOptions.HTTP2_SETTINGS_MAX_FRAME_SIZE, 16384)
                .set(io.undertow.UndertowOptions.MAX_BUFFERED_REQUEST_SIZE, 16384)
                .set(io.undertow.UndertowOptions.ENABLE_HTTP2, true)
                .set(io.undertow.UndertowOptions.ALLOW_EQUALS_IN_COOKIE_VALUE, false)
                .set(io.undertow.UndertowOptions.ENABLE_RFC6265_COOKIE_VALIDATION, false)
                .set(io.undertow.UndertowOptions.REQUIRE_HOST_HTTP11, false).getMap();
        HttpRequestParser.instance(map).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.POST, result.getRequestMethod());
        Assert.assertEquals("/wildfly-services/ejb/v1/invoke/-/ejb-remote-server-side/-/CalculatorBean/-/org.jboss.as.quickstarts.ejb.remote.stateless.RemoteCalculator/concat/[Ljava.lang.String;", result.getRequestPath());
        Assert.assertEquals("/wildfly-services/ejb/v1/invoke/-/ejb-remote-server-side/-/CalculatorBean/-/org.jboss.as.quickstarts.ejb.remote.stateless.RemoteCalculator/concat/%5BLjava.lang.String%3B", result.getRequestURI());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
        Assert.assertEquals(6, result.getRequestHeaders().size());
        Assert.assertEquals("application/x-wf-ejb-response;version=1,application/x-wf-jbmar-exception;version=1", result.getRequestHeaders().getFirst(Headers.ACCEPT));
        Assert.assertEquals("Digest username=\"quickstartUser\", uri=\"http://localhost:8080/wildfly-services/ejb/v1/invoke/-/ejb-remote-server-side/-/CalculatorBean/-/org.jboss.as.quickstarts.ejb.remote.stateless.RemoteCalculator/concat/%255BLjava.lang.String%253B\", realm=\"ApplicationRealm\", nc=00000052, cnonce=\"HgqQmHZKAJC44WA8W3yZvJmxNn9p6tpYE-gXd5BB\", algorithm=MD5, nonce=\"AAAABQABIeNiOHTsLqhR+iBs2wrWA4rOh5kc9QIjztvj4f3106O7qmH3LVQ=\", opaque=\"00000000000000000000000000000000\", qop=auth, response=\"daf568673f3998b7c50a86cfaa5e0d21\"", result.getRequestHeaders().getFirst(
                Headers.AUTHORIZATION));
        Assert.assertEquals("chunked", result.getRequestHeaders().getFirst(Headers.TRANSFER_ENCODING));
        Assert.assertEquals("application/x-wf-ejb-jbmar-invocation;version=1", result.getRequestHeaders().getFirst(Headers.CONTENT_TYPE));
        Assert.assertEquals("JSESSIONID=9ZcvdCLqUtp0hVD3C5XUfH_pczo8bos3qfHfyQDB.localhost", result.getRequestHeaders().getFirst(Headers.COOKIE));
        Assert.assertEquals("localhost:8080", result.getRequestHeaders().getFirst(Headers.HOST));
        //Assert.assertEquals(1, result.getRequestCookies().size());
    }

    @Test
    public void testHTTPClientRequestOnlySession() throws BadRequestException {
        byte[] in = ("POST /wildfly-services/ejb/v1/invoke/-/ejb-remote-server-side/-/CalculatorBean/-/org.jboss.as.quickstarts.ejb.remote.stateless.RemoteCalculator/concat/%5BLjava.lang.String%3B HTTP/1.1\r\n" +
                "Cookie: JSESSIONID=9ZcvdCLqUtp0hVD3C5XUfH_pczo8bos3qfHfyQDB.localhost\r\n").getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        OptionMap map = OptionMap.builder().set(org.xnio.Options.BALANCING_TOKENS, 1)
                .set(org.xnio.Options.REUSE_ADDRESSES, true)
                .set(io.undertow.UndertowOptions.DECODE_URL, true)
                .set(io.undertow.UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true)
                .set(io.undertow.UndertowOptions.ENABLE_STATISTICS, false)
                .set(io.undertow.UndertowOptions.RECORD_REQUEST_START_TIME, false)
                .set(io.undertow.UndertowOptions.NO_REQUEST_TIMEOUT, 60000)
                .set(io.undertow.UndertowOptions.MAX_HEADER_SIZE, 1048576)
                .set(io.undertow.UndertowOptions.MAX_COOKIES, 200)
                .set(io.undertow.UndertowOptions.MAX_PARAMETERS, 1000)
                .set(io.undertow.UndertowOptions.ALLOW_ENCODED_SLASH, false)
                .set(org.xnio.Options.TCP_NODELAY, true)
                .set(io.undertow.UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, false)
                .set(io.undertow.UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH, true)
                .set(io.undertow.UndertowOptions.HTTP2_SETTINGS_HEADER_TABLE_SIZE, 4096)
                .set(io.undertow.UndertowOptions.URL_CHARSET, "UTF-8")
                .set(org.xnio.Options.BALANCING_CONNECTIONS, 2)
                .set(io.undertow.UndertowOptions.BUFFER_PIPELINED_DATA, false)
                .set(io.undertow.UndertowOptions.MAX_ENTITY_SIZE, 10485760)
                .set(io.undertow.UndertowOptions.MAX_HEADERS, 200)
                .set(io.undertow.UndertowOptions.HTTP2_SETTINGS_INITIAL_WINDOW_SIZE, 65535)
                .set(io.undertow.UndertowOptions.HTTP2_SETTINGS_MAX_FRAME_SIZE, 16384)
                .set(io.undertow.UndertowOptions.MAX_BUFFERED_REQUEST_SIZE, 16384)
                .set(io.undertow.UndertowOptions.ENABLE_HTTP2, true)
                .set(io.undertow.UndertowOptions.ALLOW_EQUALS_IN_COOKIE_VALUE, false)
                .set(io.undertow.UndertowOptions.ENABLE_RFC6265_COOKIE_VALIDATION, false)
                .set(io.undertow.UndertowOptions.REQUIRE_HOST_HTTP11, false).getMap();
        HttpRequestParser.instance(map).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.POST, result.getRequestMethod());
        Assert.assertEquals("/wildfly-services/ejb/v1/invoke/-/ejb-remote-server-side/-/CalculatorBean/-/org.jboss.as.quickstarts.ejb.remote.stateless.RemoteCalculator/concat/[Ljava.lang.String;", result.getRequestPath());
        Assert.assertEquals("/wildfly-services/ejb/v1/invoke/-/ejb-remote-server-side/-/CalculatorBean/-/org.jboss.as.quickstarts.ejb.remote.stateless.RemoteCalculator/concat/%5BLjava.lang.String%3B", result.getRequestURI());
        Assert.assertFalse(result.isHostIncludedInRequestURI());
        Assert.assertEquals(1, result.getRequestHeaders().size());
        Assert.assertEquals("JSESSIONID=9ZcvdCLqUtp0hVD3C5XUfH_pczo8bos3qfHfyQDB.localhost", result.getRequestHeaders().getFirst(Headers.COOKIE));
        //Assert.assertEquals(1, result.getRequestCookies().size());
    }

    @Test
    public void testSth() throws BadRequestException {
        byte[] in = "GET http://myurl.com/goo;foo=bar;blah=foobar HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/goo", result.getRequestPath());
        Assert.assertEquals("http://myurl.com/goo;foo=bar;blah=foobar", result.getRequestURI());
        Assert.assertEquals(2, result.getPathParameters().size());
        Assert.assertTrue(result.isHostIncludedInRequestURI());
    }

    @Test(expected = BadRequestException.class)
    public void testLineEndingInsteadOfSpacesAfterVerb() throws BadRequestException {
        byte[] in = "GET\r/somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testLineEndingInsteadOfSpacesAfterPath() throws BadRequestException {
        byte[] in = "GET /somepath\rHTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testLineEndingInsteadOfSpacesAfterVerb2() throws BadRequestException {
        byte[] in = "GET\n/somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testLineEndingInsteadOfSpacesAfterVerb3() throws BadRequestException {
        byte[] in = "FOO\n/somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }
    @Test(expected = BadRequestException.class)
    public void testLineEndingInsteadOfSpacesAfterPath2() throws BadRequestException {
        byte[] in = "GET /somepath\nHTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }
    @Test
    public void testSimpleRequest() throws BadRequestException {
        byte[] in = "GET /somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test
    public void testDifferentCaseHeaders() throws BadRequestException {
        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        byte[] in = "GET /somepath HTTP/1.1\r\nHost: www.somehost.net\r\nhost: other\r\n\r\n".getBytes();
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertArrayEquals(result.getRequestHeaders().get("HOST").toArray(), new String[] {"www.somehost.net", "other"});
    }

    @Test(expected = BadRequestException.class)
    public void testTabInsteadOfSpaceAfterVerb() throws BadRequestException {
        byte[] in = "GET\t/somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testTabInsteadOfSpaceAfterVerb2() throws BadRequestException {
        byte[] in = "FOO\t/somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testTabInsteadOfSpaceAfterPath() throws BadRequestException {
        byte[] in = "GET\t/somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }


    @Test(expected = BadRequestException.class)
    public void testInvalidCharacterInPath() throws BadRequestException {
        byte[] in = "GET /some>path HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidCharacterInQueryString1() throws BadRequestException {
        byte[] in = "GET /somepath?foo>f=bar HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidCharacterInQueryString2() throws BadRequestException {
        byte[] in = "GET /somepath?foo=ba>r HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidCharacterInPathParam1() throws BadRequestException {
        byte[] in = "GET /somepath;foo>f=bar HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidCharacterInPathParam2() throws BadRequestException {
        byte[] in = "GET /somepath;foo=ba>r HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some\r\n    value\r\n\r\n".getBytes();
        runTest(in);
    }

    @Test
    public void testSimpleRequestWithHeaderCaching() throws BadRequestException {
        byte[] in = "GET /somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: foo\r\n\r\n".getBytes();
        runTest(in, "foo");
        in = "GET /somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader:       foo\r\n\r\n".getBytes();
        runTest(in, "foo");
        in = "GET /somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader:      some value\r\n\r\n".getBytes();
        runTest(in);
        in = "GET /somepath HTTP/1.1\r\nHost:   www.somehost.net\r\nOtherHeader: some value\r\n\r\n".getBytes();
        runTest(in);
    }


    @Test
    public void testCarriageReturnLineEnds() throws BadRequestException {

        byte[] in = "GET /somepath HTTP/1.1\rHost:   www.somehost.net\rOtherHeader: some\r    value\r\r\n".getBytes();
        runTest(in);
    }

    @Test
    public void testLineFeedsLineEnds() throws BadRequestException {
        byte[] in = "GET /somepath HTTP/1.1\nHost:   www.somehost.net\nOtherHeader: some\n    value\n\n".getBytes();
        runTest(in);
    }

    @Test(expected = BadRequestException.class)
    public void testTabWhitespace() throws BadRequestException {
        byte[] in = "GET\t/somepath\tHTTP/1.1\nHost: \t www.somehost.net\nOtherHeader:\tsome\n \t  value\n\r\n".getBytes();
        runTest(in);
    }

    @Test

    public void testCanonicalPath() throws BadRequestException {
        byte[] in = "GET http://www.somehost.net/somepath HTTP/1.1\nHost: \t www.somehost.net\nOtherHeader:\tsome\n \t  value\n\r\n".getBytes();
        final ParseState context = new ParseState(5);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertEquals("/somepath", result.getRelativePath());
        Assert.assertEquals("http://www.somehost.net/somepath", result.getRequestURI());
    }

    @Test
    public void testNoHeaders() throws BadRequestException {
        byte[] in = "GET /aa HTTP/1.1\n\n\n".getBytes();

        final ParseState context = new ParseState(0);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertTrue(context.isComplete());
        Assert.assertEquals("/aa", result.getRelativePath());
    }

    @Test
    public void testQueryParams() throws BadRequestException {
        byte[] in = "GET http://www.somehost.net/somepath?a=b&b=c&d&e&f= HTTP/1.1\nHost: \t www.somehost.net\nOtherHeader:\tsome\n \t  value\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertEquals("/somepath", result.getRelativePath());
        Assert.assertEquals("http://www.somehost.net/somepath", result.getRequestURI());
        Assert.assertEquals("a=b&b=c&d&e&f=", result.getQueryString());
        Assert.assertEquals("b", result.getQueryParameters().get("a").getFirst());
        Assert.assertEquals("c", result.getQueryParameters().get("b").getFirst());
        Assert.assertEquals("", result.getQueryParameters().get("d").getFirst());
        Assert.assertEquals("", result.getQueryParameters().get("e").getFirst());
        Assert.assertEquals("", result.getQueryParameters().get("f").getFirst());

    }

    @Test
    public void testSameHttpStringReturned() throws BadRequestException {
        byte[] in = "GET http://www.somehost.net/somepath HTTP/1.1\nHost: \t www.somehost.net\nAccept-Charset:\tsome\n \t  value\n\r\n".getBytes();

        final ParseState context1 = new ParseState(10);
        HttpServerExchange result1 = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), context1, result1);

        final ParseState context2 = new ParseState(10);
        HttpServerExchange result2 = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), context2, result2);

        Assert.assertSame(result1.getProtocol(), result2.getProtocol());
        Assert.assertSame(result1.getRequestMethod(), result2.getRequestMethod());

        for (final HttpString header : result1.getRequestHeaders().getHeaderNames()) {
            boolean found = false;
            for (final HttpString header2 : result1.getRequestHeaders().getHeaderNames()) {
                if (header == header2) {
                    found = true;
                    break;
                }
            }
            if (header.equals(Headers.HOST)) {
                Assert.assertSame(Headers.HOST, header);
            }
            Assert.assertTrue("Could not found header " + header, found);
        }
    }

    /**
     * Test for having mixed + and %20 in path for encoding spaces https://issues.jboss.org/browse/UNDERTOW-1193
     */
    @Test
    public void testPlusSignVsSpaceEncodingInPath() throws BadRequestException {
        byte[] in = "GET http://myurl.com/+/mypath%20with%20spaces HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("+ in path shouldn't be treated as space, caused probably by https://issues.jboss.org/browse/UNDERTOW-1193",
                "/+/mypath with spaces", result.getRequestPath());
        Assert.assertEquals("http://myurl.com/+/mypath%20with%20spaces", result.getRequestURI());
    }


    @Test
    public void testEmptyQueryParams() throws BadRequestException {
        byte[] in = "GET /clusterbench/requestinfo//?;?=44&test=OK;devil=3&&&&&&&&&&&&&&&&&&&&&&&&&&&&777=666 HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/clusterbench/requestinfo//", result.getRequestURI());
        Assert.assertEquals("/clusterbench/requestinfo//", result.getRequestPath());
        Assert.assertEquals(3, result.getQueryParameters().size());
        Assert.assertEquals("OK;devil=3", result.getQueryParameters().get("test").getFirst());
        Assert.assertEquals("666", result.getQueryParameters().get("777").getFirst());
        Assert.assertEquals("44", result.getQueryParameters().get(";?").getFirst());
    }

    @Test(expected = BadRequestException.class)
    public void testNonEncodedAsciiCharacters() throws UnsupportedEncodingException, BadRequestException {
        byte[] in = "GET /bÃ¥r HTTP/1.1\r\n\r\n".getBytes("ISO-8859-1");

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), context, result);
    }

    @Test
    public void testNonEncodedAsciiCharactersExplicitlyAllowed() throws UnsupportedEncodingException, BadRequestException {
        byte[] in = "GET /bÃ¥r HTTP/1.1\r\n\r\n".getBytes("ISO-8859-1");

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, true)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/bår", result.getRequestPath());
        Assert.assertEquals("/bÃ¥r", result.getRequestURI()); //not decoded
    }


    private void runTest(final byte[] in) throws BadRequestException {
        runTest(in, "some value");
    }
    private void runTest(final byte[] in, String lastHeader) throws BadRequestException {
        parseState.reset();
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.EMPTY).handle(ByteBuffer.wrap(in), parseState, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/somepath", result.getRequestURI());
        Assert.assertSame(Protocols.HTTP_1_1, result.getProtocol());

        Assert.assertEquals(2, result.getRequestHeaders().getHeaderNames().size());
        Assert.assertEquals("www.somehost.net", result.getRequestHeaders().getFirst(new HttpString("Host")));
        Assert.assertEquals(lastHeader, result.getRequestHeaders().getFirst(new HttpString("OtherHeader")));

        Assert.assertEquals(ParseState.PARSE_COMPLETE, parseState.state);
    }
}
