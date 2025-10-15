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
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.message.BasicNameValuePair;
import io.undertow.testutils.TestHttpClient;
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
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?param1=1&param2=2&param3=3");
            final List<NameValuePair> values = new ArrayList<>();
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, "UTF-8");
            post.setEntity(data);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(RESPONSE, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testPostInStream() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/aaa");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param1", "1"));
            values.add(new BasicNameValuePair("param2", "2"));
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, "UTF-8");
            post.setEntity(data);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(RESPONSE, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testPostBoth() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?param1=1&param2=2");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, "UTF-8");
            post.setEntity(data);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(RESPONSE, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testPutBothValues() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPut put = new HttpPut(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?param1=1&param2=2");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, "UTF-8");
            put.setEntity(data);
            HttpResponse result = client.execute(put);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(RESPONSE, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testPutNames() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPut put = new HttpPut(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?type=names");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param1", "1"));
            values.add(new BasicNameValuePair("param2", "2"));
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, "UTF-8");
            put.setEntity(data);
            HttpResponse result = client.execute(put);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            List<String> resList = Arrays.asList(HttpClientUtils.readResponse(result).split(","));
            Assert.assertEquals(4, resList.size());
            Assert.assertTrue(resList.contains("type"));
            Assert.assertTrue(resList.contains("param1"));
            Assert.assertTrue(resList.contains("param2"));
            Assert.assertTrue(resList.contains("param3"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testPutMap() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPut put = new HttpPut(DefaultServer.getDefaultServerURL() + "/servletContext/aaa?type=map");
            final List<NameValuePair> values = new ArrayList<>();
            values.add(new BasicNameValuePair("param1", "1"));
            values.add(new BasicNameValuePair("param2", "2"));
            values.add(new BasicNameValuePair("param3", "3"));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, "UTF-8");
            put.setEntity(data);
            HttpResponse result = client.execute(put);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            List<String> resList = Arrays.asList(HttpClientUtils.readResponse(result).split(";"));
            Assert.assertEquals(4, resList.size());
            Assert.assertTrue(resList.contains("type=map"));
            Assert.assertTrue(resList.contains("param1=1"));
            Assert.assertTrue(resList.contains("param2=2"));
            Assert.assertTrue(resList.contains("param3=3"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
