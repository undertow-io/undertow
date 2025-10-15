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

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
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
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class RewriteTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(new ServletExtension() {
                                         @Override
                                         public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                                             deploymentInfo.addOuterHandlerChainWrapper(new HandlerWrapper() {
                                                 @Override
                                                 public HttpHandler wrap(HttpHandler handler) {

                                                     byte[] data = "RewriteRule /foo1 /bar1".getBytes(StandardCharsets.UTF_8);
                                                     RewriteConfig config = RewriteConfigFactory.build(new ByteArrayInputStream(data));

                                                     return new RewriteHandler(config, handler);
                                                 }
                                             });
                                         }
                                     },
                new ServletInfo("fooServlet", PathTestServlet.class).addMapping("/bar1")
        );
    }

    @Test
    public void testRewrite() throws Exception {

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo1");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:null queryString:null servletPath:/bar1 requestUri:/servletContext/bar1", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
