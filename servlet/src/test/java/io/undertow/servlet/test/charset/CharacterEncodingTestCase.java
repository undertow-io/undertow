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

import java.io.IOException;

import javax.servlet.ServletException;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class CharacterEncodingTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(
                new ServletInfo("servlet", CharsetServlet.class)
                        .addMapping("/"));
    }

    public static byte[] toByteArray(int[] source) {
        byte[] ret = new byte[source.length];
        for (int i = 0; i < source.length; ++i) {
            ret[i] = (byte) (0xff & source[i]);
        }
        return ret;
    }

    private static final byte[] UTF16 = toByteArray(new int[]{0x00, 0x41, 0x00, 0xA9, 0x00, 0xE9, 0x03, 0x01, 0x09, 0x41, 0xD8, 0x35, 0xDD, 0x0A});
    private static final byte[] UTF8 = toByteArray(new int[]{0x41, 0xC2, 0xA9, 0xC3, 0xA9, 0xCC, 0x81, 0xE0, 0xA5, 0x81, 0xF0, 0x9D, 0x94, 0x8A});

    @Test
    public void testCharacterEncoding() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext?charset=UTF-16BE");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            byte[] response = HttpClientUtils.readRawResponse(result);
            Assert.assertArrayEquals(UTF16, response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext?charset=UTF-8");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readRawResponse(result);
            Assert.assertArrayEquals(UTF8, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
