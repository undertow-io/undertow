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

package io.undertow.servlet.test.compat.rewrite;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.compat.rewrite.RewriteConfig;
import io.undertow.servlet.compat.rewrite.RewriteConfigFactory;
import io.undertow.servlet.compat.rewrite.RewriteHandler;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.servlet.test.util.PathTestServlet;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class RewriteTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet((deploymentInfo, servletContext) ->
                        deploymentInfo.addOuterHandlerChainWrapper(handler -> {

                            byte[] data = "RewriteRule /foo1 /bar1".getBytes(StandardCharsets.UTF_8);
                            RewriteConfig config = RewriteConfigFactory.build(new ByteArrayInputStream(data));

                            return new RewriteHandler(config, handler);
                        }),
                new ServletInfo("fooServlet", PathTestServlet.class).addMapping("/bar1")
        );
    }

    @Test
    public void testRewrite() throws Exception {

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo1");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("pathInfo:null queryString:null servletPath:/bar1 requestUri:/servletContext/bar1", response);
                return null;
            });
        }
    }
}
