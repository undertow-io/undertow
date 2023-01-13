/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.dispatchingfilter;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.path.ServletPathMappingTestCase;
import io.undertow.servlet.test.util.PathTestServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.FutureResult;

import java.io.IOException;

/**
 * Tests if a dispatching filter with a lock used by both doFilter and destroy can be safely destroyed during filtering.
 * See UNDERTOW-2069.
 *
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public class DispatchingFilterTestCase {

    static FutureResult<Boolean> readyToStop = new FutureResult();

    private static DeploymentManager manager;

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ServletPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war");

        builder.addServlet(new ServletInfo("DefaultTestServlet", PathTestServlet.class)
                .addMapping("/path/default"));

        builder.addFilter(new FilterInfo("dispatching-filter", DispatchingFilter.class));
        builder.addFilterUrlMapping("dispatching-filter", "/*", DispatcherType.REQUEST);
        builder.addFilterUrlMapping("dispatching-filter", "/*", DispatcherType.FORWARD);

        manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(root);
    }

    @Test
    public void test() throws InterruptedException, ServletException, IOException {
        final FutureResult<HttpResponse> responseFuture = new FutureResult<>();
        new Thread(() -> {
            TestHttpClient client = new TestHttpClient();
            try {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path/default");
                responseFuture.setResult(client.execute(get));
            } catch (org.apache.http.client.ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                responseFuture.setException(e);
            } finally {
                client.getConnectionManager().shutdown();
            }
        }).start();
        readyToStop.getIoFuture().await();
        manager.stop();
        final HttpResponse result = responseFuture.getIoFuture().get();
        final int statusCode = result.getStatusLine().getStatusCode();
        // the deployment is stopped, we can expect an 500 or 404 (depends on OS)
        Assert.assertTrue(statusCode == StatusCodes.INTERNAL_SERVER_ERROR || statusCode == io.undertow.util.StatusCodes.NOT_FOUND);
    }
}
