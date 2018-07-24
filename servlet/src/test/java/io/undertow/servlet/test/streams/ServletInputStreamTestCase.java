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

package io.undertow.servlet.test.streams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.servlet.ServletException;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletInputStreamTestCase {

    public static final String HELLO_WORLD = "Hello World";
    public static final String BLOCKING_SERVLET = "blockingInput";
    public static final String ASYNC_SERVLET = "asyncInput";

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(
                new ServletInfo(BLOCKING_SERVLET, BlockingInputStreamServlet.class)
                        .addMapping("/" + BLOCKING_SERVLET),
                new ServletInfo(ASYNC_SERVLET, AsyncInputStreamServlet.class)
                        .addMapping("/" + ASYNC_SERVLET)
                        .setAsyncSupported(true));
    }

    @Test
    public void testBlockingServletInputStream() {
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 1000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, BLOCKING_SERVLET, false, false);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletInputStream() {
        //for(int h = 0; h < 20 ; ++h) {
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_SERVLET, false, false);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
        //}
    }

    @Test
    public void testAsyncServletInputStreamWithPreamble() {
        StringBuilder builder = new StringBuilder(2000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_SERVLET, true, false);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletInputStreamOffIoThread() {
        StringBuilder builder = new StringBuilder(2000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_SERVLET, false, true);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletInputStreamOffIoThreadWithPreamble() {
        StringBuilder builder = new StringBuilder(2000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_SERVLET, true, true);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletInputStreamWithEmptyRequestBody() {
        String message = "";
        try {
            runTest(message, ASYNC_SERVLET, false, false);
        } catch (Throwable e) {
            throw new RuntimeException("test failed", e);
        }
    }

    private void runTestViaJavaImpl(final String message, String url)
            throws IOException {
        HttpURLConnection urlcon = null;
        try {
            String uri = getBaseUrl() + "/servletContext/" + url;
            urlcon = (HttpURLConnection) new URL(uri).openConnection();
            urlcon.setInstanceFollowRedirects(true);
            urlcon.setRequestProperty("Connection", "close");
            urlcon.setRequestMethod("POST");
            urlcon.setDoInput(true);
            urlcon.setDoOutput(true);
            OutputStream os = urlcon.getOutputStream();
            os.write(message.getBytes());
            os.close();
            Assert.assertEquals(StatusCodes.OK, urlcon.getResponseCode());
            InputStream is = urlcon.getInputStream();

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int len;
            while ((len = is.read(buf)) > 0 ){
                bytes.write(buf, 0, len);
            }
            is.close();
            final String response = new String(bytes.toByteArray(), 0, bytes.size());
            if (!message.equals(response)) {
                System.out.println(String.format("response=%s", Hex.encodeHexString(response.getBytes())));
            }
            Assert.assertEquals(message, response);
        } finally {
            if (urlcon != null) {
                urlcon.disconnect();
            }
        }
    }

    protected String getBaseUrl() {
        return DefaultServer.getDefaultServerURL();
    }

    @Test
    public void testAsyncServletInputStream3() {
        String message = "to_user_id=7999&msg_body=msg3";
        for (int i = 0; i < 200; ++i) {
            try {
                runTestViaJavaImpl(message, ASYNC_SERVLET);
            } catch (Throwable e) {
                System.out.println("test failed with i equal to " + i);
                e.printStackTrace();
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }


    public void runTest(final String message, String url, boolean preamble, boolean offIOThread) throws IOException {
        TestHttpClient client = createClient();
        try {
            String uri = getBaseUrl() + "/servletContext/" + url;
            HttpPost post = new HttpPost(uri);
            if (preamble && !message.isEmpty()) {
                post.addHeader("preamble", Integer.toString(message.length() / 2));
            }
            if (offIOThread) {
                post.addHeader("offIoThread", "true");
            }
            post.setEntity(new StringEntity(message));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message.length(), response.length());
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    protected TestHttpClient createClient() {
        return new TestHttpClient();
    }

}
