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

import jakarta.servlet.ServletContext;

import io.undertow.server.handlers.RequestBufferingHandler;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 * @author Carter Kozak
 */
@RunWith(DefaultServer.class)
public class ServletInputStreamRequestBufferingTestCase extends AbstractServletInputStreamTestCase {

    @BeforeClass
    public static void setup() {
        DeploymentUtils.setupServlet(
                new ServletExtension() {
                    @Override
                    public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                        deploymentInfo.addInitialHandlerChainWrapper(new RequestBufferingHandler.Wrapper(1));
                    }
                },
                new ServletInfo(BLOCKING_SERVLET, BlockingInputStreamServlet.class)
                        .addMapping("/" + BLOCKING_SERVLET),
                new ServletInfo(ASYNC_SERVLET, AsyncInputStreamServlet.class)
                        .addMapping("/" + ASYNC_SERVLET)
                        .setAsyncSupported(true));
    }
}
