/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.listener.request.async;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Test that AsyncListener failures do not block execution of other listeners.
 *
 * @author ckozak
 */
@RunWith(DefaultServer.class)
public class AsyncListenerExceptionTest {

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo runtime = new ServletInfo("runtime", RuntimeExceptionServlet.class)
                .addMapping("/runtime")
                .setAsyncSupported(true);
        ServletInfo io = new ServletInfo("io", IOExceptionServlet.class)
                .addMapping("/io")
                .setAsyncSupported(true);
        ServletInfo error = new ServletInfo("error", ErrorServlet.class)
                .addMapping("/error")
                .setAsyncSupported(true);

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(AsyncListenerExceptionTest.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlets(runtime, io, error);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Before
    public void setUp() {
        AbstractAsyncServlet.QUEUE.clear();
    }

    @Test
    public void onCompleteThrowsRuntimeException() throws IOException, InterruptedException {
        doTest("runtime", false);
    }

    @Test
    public void onCompleteThrowsIOException() throws IOException, InterruptedException {
        doTest("io", false);
    }

    @Test
    public void onCompleteThrowsError() throws IOException, InterruptedException {
        doTest("error", false);
    }

    @Test
    public void onTimeoutThrowsRuntimeException() throws IOException, InterruptedException {
        doTest("runtime", true);
    }

    @Test
    public void onTimeoutThrowsIOException() throws IOException, InterruptedException {
        doTest("io", true);
    }

    @Test
    public void onTimeoutThrowsError() throws IOException, InterruptedException {
        doTest("error", true);
    }

    private void doTest(String urlTail, boolean timeout) throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + urlTail);
            if (timeout) {
                get.addHeader("timeout", "true");
            }
            HttpResponse result = client.execute(get);
            Assert.assertEquals(timeout ? 500 : 200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            List<String> expected = new LinkedList<>();
            expected.add("onComplete");
            expected.add("onComplete");
            if (timeout) {
                expected.add("onTimeout");
                expected.add("onTimeout");
            }
            List<String> actual = new LinkedList<>();
            for (int i = 0; i < expected.size(); i++) {
                actual.add(AbstractAsyncServlet.QUEUE.poll(10, TimeUnit.SECONDS));
            }
            actual.sort(Comparator.naturalOrder());
            Assert.assertEquals(expected, actual);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    public abstract static class AbstractAsyncServlet extends HttpServlet {
        static final BlockingQueue<String> QUEUE = new LinkedBlockingDeque<>();
        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
                throws ServletException, IOException {
            AsyncContext context = req.startAsync();
            context.setTimeout(1000);
            for (int i = 0; i < 2; i++) {
                context.addListener(new AsyncListener() {
                    @Override
                    public void onComplete(AsyncEvent asyncEvent) throws IOException {
                        QUEUE.add("onComplete");
                        throwException();
                    }

                    @Override
                    public void onTimeout(AsyncEvent asyncEvent) throws IOException {
                        QUEUE.add("onTimeout");
                        throwException();
                    }

                    @Override
                    public void onError(AsyncEvent asyncEvent) throws IOException {
                        QUEUE.add("onError");
                        throwException();
                    }

                    @Override
                    public void onStartAsync(AsyncEvent asyncEvent) throws IOException {
                        QUEUE.add("onStartAsync");
                    }
                });
            }
            if (req.getHeader("timeout") == null) {
                context.complete();
            }
        }

        protected abstract void throwException() throws IOException;
    }

    public static final class RuntimeExceptionServlet extends AbstractAsyncServlet {
        @Override
        protected void throwException() throws IOException {
            throw new RuntimeException();
        }
    }

    public static final class IOExceptionServlet extends AbstractAsyncServlet {
        @Override
        protected void throwException() throws IOException {
            throw new IOException();
        }
    }

    public static final class ErrorServlet extends AbstractAsyncServlet {
        @Override
        protected void throwException() throws IOException {
            throw new Error();
        }
    }
}
