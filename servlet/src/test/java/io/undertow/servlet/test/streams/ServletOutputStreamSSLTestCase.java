/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.test.streams;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;

@RunWith(DefaultServer.class)
public class ServletOutputStreamSSLTestCase extends ServletOutputStreamTestCase {

    @BeforeClass
    public static void ssl() throws Exception {
        DefaultServer.startSSLServer();
    }

    @AfterClass
    public static void stopssl() throws IOException {
        DefaultServer.stopSSLServer();
    }


    @Override
    protected TestHttpClient createClient() {
        TestHttpClient client = super.createClient();
        client.setSSLContext(DefaultServer.createClientSslContext());
        return client;
    }

    @Override
    protected String getBaseUrl() {
        return DefaultServer.getDefaultServerSSLAddress();
    }

    @Override public void testAsyncServletOutputStreamOffIOThread() {
        // FIXME UNDERTOW-1948 temporarily ignore the test (throws SocketTimeoutException)
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows") && DefaultServer.isProxy() && DefaultServer.isAjp());
        super.testAsyncServletOutputStreamOffIOThread();
    }

    @Override public void testAsyncServletOutputStreamWithPreable() {
        // FIXME UNDERTOW-1948 temporarily ignore the exception (throws SocketTimeoutException)
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows") && DefaultServer.isProxy() && DefaultServer.isAjp());
        super.testAsyncServletOutputStreamWithPreable();
    }
}
