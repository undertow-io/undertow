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

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Artemy Osipov
 */
@RunWith(DefaultServer.class)
public class DefaultCharacterEncodingTestCase {

    private void setup(final String defaultEncoding) throws ServletException {
        DeploymentUtils.setupServlet(new ServletExtension() {
                                         @Override
                                         public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                                             if (defaultEncoding != null) {
                                                 deploymentInfo.setDefaultEncoding(defaultEncoding);
                                             }
                                         }
                                     },
                Servlets.servlet("servlet", DefaultCharacterEncodingServlet.class)
                        .addMapping("/"));
    }

    private void testDefaultEncoding(String defaultCharacterEncoding,
                                     String expectedRequestCharacterEncoding,
                                     String expectedResponseCharacterEncoding) throws IOException, ServletException {
        setup(defaultCharacterEncoding);
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Unexpected request character encoding",
                    expectedRequestCharacterEncoding, readParameter(response, "requestCharacterEncoding"));
            Assert.assertEquals("Unexpected response character encoding",
                    expectedResponseCharacterEncoding, readParameter(response, "responseCharacterEncoding"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private String readParameter(String response, String parameter) {
        Pattern pattern = Pattern.compile(parameter + "=(.*?);");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    @Test
    public void testDefaultEncodingNotSet() throws IOException, ServletException {
        testDefaultEncoding(null, "null", "ISO-8859-1");
    }

    @Test
    public void testDefaultEncodingSetEqualDefault() throws IOException, ServletException {
        testDefaultEncoding("ISO-8859-1", "ISO-8859-1", "ISO-8859-1");
    }

    @Test
    public void testDefaultEncodingSetNotEqualDefault() throws IOException, ServletException {
        testDefaultEncoding("UTF-8", "UTF-8", "UTF-8");
    }
}
