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

package io.undertow.servlet.test.response.contenttype;

import java.net.URLEncoder;

import jakarta.servlet.ServletException;

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
public class ContentTypeCharsetTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(new ServletInfo("charset", ContentTypeServlet.class)
                .addMapping("/*"));
    }

    @Test
    public void testCharsetAndContentType() throws Exception {
        runtest("text/html", "UTF8", "text/html;charset=UTF8", "text/html;charset=UTF8\nUTF8");
        runtest("text/html", "", "text/html;charset=ISO-8859-1", "text/html;charset=ISO-8859-1\nISO-8859-1");
        runtest("text/html;   charset=UTF8", "", "text/html;charset=UTF8", "text/html;charset=UTF8\nUTF8");
        runtest("text/html;   charset=\"UTF8\"", "", "text/html;charset=UTF8", "text/html;charset=UTF8\nUTF8");
        runtest("text/html;   charset=UTF8; boundary=someString;", "", "text/html; boundary=someString;charset=UTF8", "text/html; boundary=someString;charset=UTF8\nUTF8");
        runtest("text/html;   charset=UTF8; boundary=someString;   ", "", "text/html; boundary=someString;charset=UTF8", "text/html; boundary=someString;charset=UTF8\nUTF8");
        runtest("multipart/related; type=\"text/xml\"; boundary=\"uuid:ce7d652a-d035-42fa-962c-5b8315084e32\"; start=\"<root.message@cxf.apache.org>\"; start-info=\"text/xml\"", "", "multipart/related; type=\"text/xml\"; boundary=\"uuid:ce7d652a-d035-42fa-962c-5b8315084e32\"; start=\"<root.message@cxf.apache.org>\"; start-info=\"text/xml\";charset=ISO-8859-1", "multipart/related; type=\"text/xml\"; boundary=\"uuid:ce7d652a-d035-42fa-962c-5b8315084e32\"; start=\"<root.message@cxf.apache.org>\"; start-info=\"text/xml\";charset=ISO-8859-1\nISO-8859-1");

    }

    private void runtest(String contentType, String charset, String expectedContentType, String expectedBody) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/test?contentType=" + URLEncoder.encode(contentType) + "&charset=" + URLEncoder.encode(charset));
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(expectedContentType, result.getHeaders("Content-Type")[0].getValue());
            Assert.assertEquals(expectedBody, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
