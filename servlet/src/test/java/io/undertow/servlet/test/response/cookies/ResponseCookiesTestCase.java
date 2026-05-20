/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.response.cookies;

import java.util.Arrays;
import java.util.Comparator;

import jakarta.servlet.ServletException;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.core5.http.Header;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test for response.addCookie
 *
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public class ResponseCookiesTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(
                new ServletInfo("add-cookies", AddCookiesServlet.class)
                        .addMapping("/add-cookies"),
                new ServletInfo("duplicate-cookies", DuplicateCookiesServlet.class)
                        .addMapping("/duplicate-cookies"),
                new ServletInfo("overwrite-cookies", OverwriteCookiesServlet.class)
                        .addMapping("/overwrite-cookies"),
                new ServletInfo("jsessionid-cookies", JSessionIDCookiesServlet.class)
                        .addMapping("/jsessionid-cookies"));
    }

    @Test
    public void addCookies() throws Exception {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/add-cookies");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());

                final String response = HttpClientUtils.readResponse(result);
                assertEquals("Served at: /servletContext", response);

                final Header[] setCookieHeaders = result.getHeaders("Set-Cookie");
                assertEquals(2, setCookieHeaders.length);
                assertTrue(setCookieHeadersContainsValue("test1=test1; path=/test", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test2=test2", setCookieHeaders));
                return null;
            });
        }
    }

    @Test
    public void duplicateCookies() throws Exception {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/duplicate-cookies");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());

                final String response = HttpClientUtils.readResponse(result);
                assertEquals("Served at: /servletContext", response);

                final Header[] setCookieHeaders = result.getHeaders("Set-Cookie");
                assertEquals(7, setCookieHeaders.length);
                Arrays.sort(setCookieHeaders, Comparator.comparing(Object::toString));
                assertTrue(setCookieHeadersContainsValue("test1=test1; path=/test1_1", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test1=test1; path=/test1_1", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test1=test1; path=/test1_2", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test2=test2; path=/test2", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test2=test2; path=/test2; domain=www.domain2.com", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test3=test3", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test3=test3; domain=www.domain3-1.com", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test3=test3; domain=www.domain3-2.com", setCookieHeaders));
                return null;
            });
        }
    }

    @Test
    public void overwriteCookies() throws Exception {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/overwrite-cookies");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());

                final String response = HttpClientUtils.readResponse(result);
                assertEquals("Served at: /servletContext", response);

                final Header[] setCookieHeaders = result.getHeaders("Set-Cookie");
                assertEquals(5, setCookieHeaders.length);
                Arrays.sort(setCookieHeaders, Comparator.comparing(Object::toString));
                assertTrue(setCookieHeadersMatchesValue("JSESSIONID=.*; Path=/servletContext", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test=test10; domain=www.domain.com", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test=test2; path=/test", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test=test5", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValue("test=test8; path=/test; domain=www.domain.com", setCookieHeaders));
                return null;
            });
        }
    }

    @Test
    public void jsessionIdCookies() throws Exception {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/jsessionid-cookies");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());

                final String response = HttpClientUtils.readResponse(result);
                assertEquals("Served at: /servletContext", response);

                final Header[] setCookieHeaders = result.getHeaders("Set-Cookie");
                assertEquals(4, setCookieHeaders.length);
                assertTrue(setCookieHeadersContainsValueStartingWithPrefix("JSESSIONID=_bug_fix; Path=/path3; Max-Age=500; Expires=", setCookieHeaders));
                assertTrue(setCookieHeadersContainsValueStartingWithPrefix("JSESSIONID=_bug_fix; Path=/path4; Max-Age=1000; Expires=", setCookieHeaders));
                assertTrue(setCookieHeadersMatchesValue("JSESSIONID=.*; Path=/servletContext", setCookieHeaders));
                return null;
            });
        }
    }

    private static boolean setCookieHeadersContainsValue(final String value, final Header[] setCookieHeaders) {
        if (setCookieHeaders == null) return false;
        for (Header h : setCookieHeaders) {
            if (value.equalsIgnoreCase(h.getValue())) return true;
        }
        return false;
    }

    private static boolean setCookieHeadersContainsValueStartingWithPrefix(final String prefix, final Header[] setCookieHeaders) {
        if (setCookieHeaders == null) return false;
        for (Header h : setCookieHeaders) {
            if (h.getValue().startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean setCookieHeadersMatchesValue(final String regexp, final Header[] setCookieHeaders) {
        if (setCookieHeaders == null) return false;
        for (Header h : setCookieHeaders) {
            if (h.getValue().matches(regexp)) return true;
        }
        return false;
    }
}
