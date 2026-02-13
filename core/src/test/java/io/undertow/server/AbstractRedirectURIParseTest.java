/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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
package io.undertow.server;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Abstract test for URIs with redirect-uri params.
 *
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
abstract class AbstractRedirectURIParseTest {

    private static Map<String, Deque<String>> queryParams;

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(exchange -> queryParams = Collections.unmodifiableMap(exchange.getQueryParameters()));
    }

    @Before
    public final void clearQueryParams() {
        queryParams = null;
    }

    // URI has to be percent-encoded unless allowUnescapedCharactersInUrl is enabled
    public String encodeURI(String uri) {
        return URI.create(uri).toASCIIString();
    }

    @Test
    public void testSimpleRedirectURI() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final HttpGet get = new HttpGet(encodeURI(DefaultServer.getDefaultServerURL() + "/redirect?test=code&redirect_uri=192.168.1.1:50"));
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                return null;
            });
            assertNotNull(queryParams);
            assertEquals(2, queryParams.size());

            final Deque<String> testParamValues = queryParams.get("test");
            assertNotNull(testParamValues);
            assertEquals(1, testParamValues.size());
            assertEquals("code", testParamValues.getFirst());

            final Deque<String> redirectURIParamValues = queryParams.get("redirect_uri");
            assertNotNull(redirectURIParamValues);
            assertEquals(1, redirectURIParamValues.size());
            assertEquals("192.168.1.1:50", redirectURIParamValues.getFirst());
        }
    }

    @Test
    public void testUnescapedURI1() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.withEncoding(StandardCharsets.UTF_8).build()) {
            final HttpGet get = new HttpGet(encodeURI(DefaultServer.getDefaultServerURL() + "/redirect?test=code&param=한글이름_ahoy&redirect_uri=http://localhost:8080/helloworld/한글이름_test.html"));
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                return null;
            });
            assertNotNull(queryParams);
            assertEquals(3, queryParams.size());

            final Deque<String> testParamValues = queryParams.get("test");
            assertNotNull(testParamValues);
            assertEquals(1, testParamValues.size());
            assertEquals("code", testParamValues.getFirst());

            final Deque<String> redirectURIParamValues = queryParams.get("redirect_uri");
            assertNotNull(redirectURIParamValues);
            assertEquals(1, redirectURIParamValues.size());
            assertEquals("http://localhost:8080/helloworld/한글이름_test.html", redirectURIParamValues.getFirst());

            final Deque<String> paramValues = queryParams.get("param");
            assertNotNull(paramValues);
            assertEquals(1, paramValues.size());
            assertEquals("한글이름_ahoy", paramValues.getFirst());
        }
    }

    @Test
    public void testUnescapedURI2() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final HttpGet get = new HttpGet(encodeURI(DefaultServer.getDefaultServerURL() + "/redirect?test=code&redirect_uri=http://localhost:8080/redirect?param1=value1&param2=value2"));
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                return null;
            });
            assertNotNull(queryParams);
            assertEquals(3, queryParams.size());

            final Deque<String> testParamValues = queryParams.get("test");
            assertNotNull(testParamValues);
            assertEquals(1, testParamValues.size());
            assertEquals("code", testParamValues.getFirst());

            final Deque<String> redirectURIParamValues = queryParams.get("redirect_uri");
            assertNotNull(redirectURIParamValues);
            assertEquals(1, redirectURIParamValues.size());
            assertEquals("http://localhost:8080/redirect?param1=value1", redirectURIParamValues.getFirst());

            final Deque<String> param2Values = queryParams.get("param2");
            assertNotNull(param2Values);
            assertEquals(1, param2Values.size());
            assertEquals("value2", param2Values.getFirst());
        }
    }

    @Test
    public void testEncodedCharacters1() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final HttpGet get = new HttpGet(encodeURI(DefaultServer.getDefaultServerURL() + "/redirect?test=code&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fredirect%3Fparam1%3Dvalue1%26param2%3Dvalue2"));
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                return null;
            });
            assertNotNull(queryParams);
            assertEquals(2, queryParams.size());

            final Deque<String> testParamValues = queryParams.get("test");
            assertNotNull(testParamValues);
            assertEquals(1, testParamValues.size());
            assertEquals("code", testParamValues.getFirst());

            final Deque<String> redirectURIParamValues = queryParams.get("redirect_uri");
            assertNotNull(redirectURIParamValues);
            assertEquals(1, redirectURIParamValues.size());
            assertEquals("http://localhost:8080/redirect?param1=value1&param2=value2", redirectURIParamValues.getFirst());
        }
    }

    @Test
    public void testEncodedCharacters2() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final HttpGet get = new HttpGet(encodeURI(DefaultServer.getDefaultServerURL() +
                    "/redirect?test=code123&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fpath%2Finput%3Fp_p_id%3DJANE_DOE%26p_p_lifecycle%3D1%26_checkId_javax.portlet.action%3Dredirect"));
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                return null;
            });
            assertNotNull(queryParams);
            assertEquals(2, queryParams.size());

            final Deque<String> testParamValues = queryParams.get("test");
            assertNotNull(testParamValues);
            assertEquals(1, testParamValues.size());
            assertEquals("code123", testParamValues.getFirst());

            final Deque<String> redirectURIParamValues = queryParams.get("redirect_uri");
            assertNotNull(redirectURIParamValues);
            assertEquals(1, redirectURIParamValues.size());
            assertEquals("http://localhost:8080/path/input?p_p_id=JANE_DOE&p_p_lifecycle=1&_checkId_javax.portlet.action=redirect",
                    redirectURIParamValues.getFirst());
        }
    }
}