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

package io.undertow.servlet.test.charset;

import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static io.undertow.servlet.Servlets.servlet;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class DefaultCharsetTestCase {


    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet((deploymentInfo, servletContext) ->
                        deploymentInfo.setDefaultEncoding("UTF-8"),
                servlet("servlet", DefaultCharsetServlet.class)
                        .addMapping("/writer"),
                servlet("form", DefaultCharsetFormParserServlet.class)
                        .addMapping("/form"));
    }

    public static byte[] toByteArray(int[] source) {
        byte[] ret = new byte[source.length];
        for (int i = 0; i < source.length; ++i) {
            ret[i] = (byte) (0xff & source[i]);
        }
        return ret;
    }

    private static final byte[] UTF8 = toByteArray(new int[]{0x41, 0xC2, 0xA9, 0xC3, 0xA9, 0xCC, 0x81, 0xE0, 0xA5, 0x81, 0xF0, 0x9D, 0x94, 0x8A});

    @Test
    public void testCharacterEncodingWriter() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/writer");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                byte[] response = HttpClientUtils.readRawResponse(result);
                Assert.assertArrayEquals(UTF8, response);
                return null;
            });


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/writer?array=true");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                byte[] response = HttpClientUtils.readRawResponse(result);
                Assert.assertArrayEquals(UTF8, response);
                return null;
            });
        }
    }


    @Test
    public void testCharacterEncodingFormParser() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/form");
            post.setEntity(new UrlEncodedFormEntity(Collections.singletonList(new BasicNameValuePair("\u0041\u00A9\u00E9\u0301\u0941\uD835\uDD0A", "\u0041\u00A9\u00E9\u0301\u0941\uD835\uDD0A")), StandardCharsets.UTF_8));
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                byte[] response = HttpClientUtils.readRawResponse(result);
                Assert.assertArrayEquals(UTF8, response);
                return null;
            });
        }
    }
}
