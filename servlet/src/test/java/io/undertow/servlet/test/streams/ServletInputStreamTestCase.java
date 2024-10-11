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

package io.undertow.servlet.test.streams;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletInputStreamTestCase extends AbstractServletInputStreamTestCase {

    @BeforeClass
    public static void setup() {
        DeploymentUtils.setupServlet(
                new ServletInfo(BLOCKING_SERVLET, BlockingInputStreamServlet.class)
                        .addMapping("/" + BLOCKING_SERVLET),
                new ServletInfo(ASYNC_SERVLET, AsyncInputStreamServlet.class)
                        .addMapping("/" + ASYNC_SERVLET)
                        .setAsyncSupported(true),
                new ServletInfo(ASYNC_EAGER_SERVLET, EagerAsyncInputStreamServlet.class)
                        .addMapping("/" + ASYNC_EAGER_SERVLET)
                        .setAsyncSupported(true));
    }

    @Test
    public void testAsyncServletInputStreamEagerIsReady() {
        //for(int h = 0; h < 20 ; ++h) {
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_EAGER_SERVLET, false, false);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
        //}
    }

    @Override @Test @Ignore ("UNDERTOW-1927 503 result received sporadically") // FIXME
    public void testAsyncServletInputStreamInParallelOffIoThread() {
    }

    @Override @Test @Ignore ("UNDERTOW-1927 503 result received sporadically") // FIXME
    public void testAsyncServletInputStreamInParallel() {
    }
}
