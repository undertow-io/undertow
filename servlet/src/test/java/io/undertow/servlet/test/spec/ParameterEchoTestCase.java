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

package io.undertow.servlet.test.spec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.ParameterEchoServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.StatusCodes;
import io.undertow.testutils.TestHttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Istvan Szabo
 */
@RunWith(DefaultServer.class)
public class ParameterEchoTestCase {

    public static final String RESPONSE = "param1=\'1\'param2=\'2\'param3=\'3\'";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", ParameterEchoServlet.class)
                .addMapping("/aaa");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(ParameterEchoTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlet(s);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testPostInUrl() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?param1=1&param2=2&param3=3");
            final List<NameValuePair> values = new ArrayList<>();
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, StandardCharsets.UTF_8);
            post.setEntity(data);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(RESPONSE, response);
                return null;
            });
        }
    }

    @Test
    public void testPostInStream() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/aaa");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param1", "1"));
            values.add(new BasicNameValuePair("param2", "2"));
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, StandardCharsets.UTF_8);
            post.setEntity(data);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(RESPONSE, response);
                return null;
            });
        }
    }

    @Test
    public void testPostBoth() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?param1=1&param2=2");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, StandardCharsets.UTF_8);
            post.setEntity(data);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(RESPONSE, response);
                return null;
            });
        }
    }

    @Test
    public void testPutBothValues() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPut put = new HttpPut(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?param1=1&param2=2");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, StandardCharsets.UTF_8);
            put.setEntity(data);
            client.execute(put, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(RESPONSE, response);
                return null;
            });
        }
    }


    @Test
    public void testPutNames() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPut put = new HttpPut(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?type=names");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param1", "1"));
            values.add(new BasicNameValuePair("param2", "2"));
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, StandardCharsets.UTF_8);
            put.setEntity(data);
            client.execute(put, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                List<String> resList = Arrays.asList(HttpClientUtils.readResponse(result).split(","));
                Assert.assertEquals(4, resList.size());
                Assert.assertTrue(resList.contains("type"));
                Assert.assertTrue(resList.contains("param1"));
                Assert.assertTrue(resList.contains("param2"));
                Assert.assertTrue(resList.contains("param3"));
                return null;
            });
        }
    }

    @Test
    public void testPutMap() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPut put = new HttpPut(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?type=map");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param1", "1"));
            values.add(new BasicNameValuePair("param2", "2"));
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, StandardCharsets.UTF_8);
            put.setEntity(data);
            client.execute(put, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                List<String> resList = Arrays.asList(HttpClientUtils.readResponse(result).split(";"));
                Assert.assertEquals(4, resList.size());
                Assert.assertTrue(resList.contains("type=map"));
                Assert.assertTrue(resList.contains("param1=1"));
                Assert.assertTrue(resList.contains("param2=2"));
                Assert.assertTrue(resList.contains("param3=3"));
                return null;
            });
        }
    }
}
