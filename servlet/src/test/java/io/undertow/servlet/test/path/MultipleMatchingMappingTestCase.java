/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.path;

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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.MappingMatch;
import java.io.IOException;

/**
 * Test cases for <a href="https://issues.redhat.com/browse/UNDERTOW-1844">UNDERTOW-1844</a>.
 *
 * @author Carter Kozak
 */
@RunWith(DefaultServer.class)
public class MultipleMatchingMappingTestCase {
    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(
                new ServletInfo("path", GetMappingServlet.class)
                        .addMapping("/path/*")
                        .addMapping("/*")
                        // This extension prefix is impossible to reach due to the '/*' path  match.
                        .addMapping("*.ext"));

    }

    @Test
    public void testMatchesPathAndExtension1() {
        doTest("/foo.ext", MappingMatch.PATH, "foo.ext", "/*", "path");
    }

    @Test
    public void testMatchesPathAndExtension2() {
        doTest("/other/foo.ext", MappingMatch.PATH, "other/foo.ext", "/*", "path");
    }

    @Test
    public void testMatchesPathAndExtension3() {
        doTest("/path/foo.ext", MappingMatch.PATH, "foo.ext", "/path/*", "path");
    }

    private static void doTest(
            // Input request path excluding the servlet context path
            String path,
            // Expected HttpServletMapping result values
            MappingMatch mappingMatch,
            String matchValue,
            String pattern,
            String servletName) {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext" + path);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            String expected = String.format("Mapping match:%s\nMatch value:%s\nPattern:%s\nServlet:%s",
                    mappingMatch.name(), matchValue, pattern, servletName);
            Assert.assertEquals(expected, response);
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
