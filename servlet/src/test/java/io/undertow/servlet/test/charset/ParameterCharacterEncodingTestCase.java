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

import io.undertow.servlet.Servlets;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static io.undertow.servlet.Servlets.multipartConfig;

/**
 * @author Matej Lazar
 */
@RunWith(DefaultServer.class)
public class ParameterCharacterEncodingTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(Servlets.servlet("servlet", EchoServlet.class)
                .addMapping("/")
                .setMultipartConfig(multipartConfig(null, 0, 0, 0)));
    }

    @Test
    public void testUrlCharacterEncoding() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String message = "abc (\"čšž\")";
            String charset = "UTF-8";
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext?charset=" + charset + "&message=" + URLEncoder.encode(message, "UTF-8"));
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testUrlPathEncodings() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String message = "abc(\"čšž\")";
            String charset = "UTF-8";
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + URLEncoder.encode(message, "UTF-8") + "?charset=" + charset);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testMultipartCharacterEncoding() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String message = "abcčšž";
            String charset = "UTF-8";

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext");

            MultipartEntity multipart = new MultipartEntity();
            multipart.addPart("charset", new StringBody(charset, Charset.forName(charset)));
            multipart.addPart("message", new StringBody(message, Charset.forName(charset)));
            post.setEntity(multipart);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testFormDataCharacterEncoding() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String message = "abcčšž";
            String charset = "UTF-8";

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("charset", charset));
            values.add(new BasicNameValuePair("message", message));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, "UTF-8");
            post.setEntity(data);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
