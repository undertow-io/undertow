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

package io.undertow.servlet.test.multipart.forward;

import static io.undertow.servlet.Servlets.multipartConfig;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;

import java.io.IOException;
import java.util.Arrays;

import jakarta.servlet.ServletException;

import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DefaultServer.class)
public class MultiPartForwardTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(
            Servlets.servlet("MultiPartCapableServlet", MultiPartCapableServlet.class)
                .addMapping("/multipart")
                .setMultipartConfig(multipartConfig(null, 0, 0, 0)),
            Servlets.servlet("ForwardingServlet", ForwardingServlet.class)
                .addMapping("/forward"));
    }

    @Test
    public void urlEncodedFormRequestDirectlyToMultipartServlet() throws IOException {

        String response = sendRequest("/multipart", createUrlEncodedFormPostEntity());

        Assert.assertEquals("Params:\r\n"
            + "foo: bar", response);

    }

    @Test
    public void urlEncodedFormRequestForwardedToMultipartServlet() throws IOException {

        String response = sendRequest("/forward", createUrlEncodedFormPostEntity());

        Assert.assertEquals("Params:\r\n"
            + "foo: bar", response);

    }

    @Test
    public void multiPartFormRequestDirectlyToMultipartServlet() throws IOException {

        String response = sendRequest("/multipart", createMultiPartFormPostEntity());

        Assert.assertEquals("Params:\r\n"
            + "foo: bar", response);

    }

    @Test
    public void multiPartFormRequestForwardedToMultipartServlet() throws IOException {

        String response = sendRequest("/forward", createMultiPartFormPostEntity());

        Assert.assertEquals("Params:\r\n"
            + "foo: bar", response);

    }

    private String sendRequest(String path, HttpEntity postEntity) throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext" + path;

            HttpPost post = new HttpPost(uri);
            post.setEntity(postEntity);

            return client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                return HttpClientUtils.readResponse(result).trim();
            });
        }
    }

    private HttpEntity createMultiPartFormPostEntity() throws IOException {
        return MultipartEntityBuilder.create().setMode(HttpMultipartMode.LEGACY)
            .addPart("foo", new StringBody("bar", ContentType.TEXT_PLAIN)).build();
    }

    private UrlEncodedFormEntity createUrlEncodedFormPostEntity() throws IOException {
        BasicNameValuePair nameValuePair = new BasicNameValuePair("foo", "bar");
        return new UrlEncodedFormEntity(Arrays.asList(nameValuePair));
    }

}
