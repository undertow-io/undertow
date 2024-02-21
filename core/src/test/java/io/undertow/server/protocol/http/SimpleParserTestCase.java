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

import io.undertow.UndertowOptions;
import io.undertow.testutils.category.UnitTest;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.BadRequestException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.xnio.OptionMap;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

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
    public void testEncodedSlashDisallowed_DECODE_FLAG() throws BadRequestException {
        byte[] in = "GET /somepath%2FotherPath HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.DECODE_SLASH, false)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertSame(Methods.GET, result.getRequestMethod());
        Assert.assertEquals("/somepath%2FotherPath", result.getRequestURI());
        Assert.assertEquals("/somepath%2FotherPath", result.getRequestPath());
    }

    @Test
    public void testEncodedSlashAllowed_DECODE_FLAG() throws BadRequestException {
        byte[] in = "GET /somepath%2fotherPath HTTP/1.1\r\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        //this also tests override of UndertowOptions.ALLOW_ENCODED_SLASH
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.ALLOW_ENCODED_SLASH, false, UndertowOptions.DECODE_SLASH, true)).handle(ByteBuffer.wrap(in), context, result);
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
    public void testQueryParams_DECODE_FLAG() throws BadRequestException {
        byte[] in = "GET http://www.somehost.net/somepath?a=b%3e%2F&b=c&d&e&f= HTTP/1.1\nHost: \t www.somehost.net\nOtherHeader:\tsome\n \t value\n\r\n".getBytes();

        final ParseState context = new ParseState(10);
        HttpServerExchange result = new HttpServerExchange(null);
        HttpRequestParser.instance(OptionMap.create(UndertowOptions.DECODE_SLASH, false)).handle(ByteBuffer.wrap(in), context, result);
        Assert.assertEquals("/somepath", result.getRelativePath());
        Assert.assertEquals("http://www.somehost.net/somepath", result.getRequestURI());
        Assert.assertEquals("a=b%3e%2F&b=c&d&e&f=", result.getQueryString());
        Assert.assertEquals("b>/", result.getQueryParameters().get("a").getFirst());
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
        Assert.assertEquals("/bår", result.getRequestURI()); //!not decoded
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
