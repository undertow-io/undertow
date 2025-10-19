/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.handlers;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class HttpContinueSslServletTestCase extends AbstractHttpContinueServletTestCase {

    protected String getServerAddress() {
        return DefaultServer.getDefaultServerSSLAddress();
    }

    protected TestHttpClient getClient() {
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        return client;
    }

    @BeforeClass
    public static void before() throws Exception {
        AbstractHttpContinueServletTestCase.before();
        DefaultServer.startSSLServer();
    }

    @AfterClass
    public static void after() throws IOException {
        DefaultServer.stopSSLServer();
    }
}
