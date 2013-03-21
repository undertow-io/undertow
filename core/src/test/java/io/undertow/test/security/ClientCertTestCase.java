/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.test.security;

import static org.junit.Assert.assertEquals;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityNotification.EventType;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.test.utils.AjpIgnore;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.TestHttpClient;

import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test case covering the core of Client-Cert
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@AjpIgnore
@RunWith(DefaultServer.class)
public class ClientCertTestCase extends AuthenticationTestBase {

    private static SSLContext clientSSLContext;

    @Override
    protected AuthenticationMechanism getTestMechanism() {
        return new ClientCertAuthenticationMechanism();
    }

    @BeforeClass
    public static void startSSL() throws Exception {
        DefaultServer.startSSLServer();
        clientSSLContext = DefaultServer.getClientSSLContext();
    }

    @AfterClass
    public static void stopSSL() throws Exception {
        clientSSLContext = null;
        DefaultServer.stopSSLServer();
    }

    @Test
    public void testClientCertSuccess() throws Exception {
        setAuthenticationChain();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(clientSSLContext);
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
        HttpResponse result = client.execute(get);
        assertEquals(200, result.getStatusLine().getStatusCode());

        Header[] values = result.getHeaders("ProcessedBy");
        assertEquals("ProcessedBy Headers", 1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());

        values = result.getHeaders("AuthenticatedUser");
        assertEquals("AuthenticatedUser Headers", 1, values.length);
        assertEquals("CN=Test Client,OU=OU,O=Org,L=City,ST=State,C=GB", values[0].getValue());
        HttpClientUtils.readResponse(result);
        assertSingleNotificationType(EventType.AUTHENTICATED);
    }

}
