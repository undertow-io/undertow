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
package io.undertow.server.security;

import java.util.Collections;
import java.util.List;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityNotification.EventType;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;
import io.undertow.connector.PooledByteBuffer;

import javax.net.ssl.SSLContext;

import static org.junit.Assert.assertEquals;
import static org.xnio.Options.SSL_CLIENT_AUTH_MODE;
import static org.xnio.SslClientAuthMode.NOT_REQUESTED;

/**
 * Test case covering the core of Client-Cert
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class ClientCertRenegotiationTestCase extends AuthenticationTestBase {

    private static SSLContext clientSSLContext;

    @Override
    protected List<AuthenticationMechanism> getTestMechanisms() {
        AuthenticationMechanism mechanism = new ClientCertAuthenticationMechanism();

        return Collections.singletonList(mechanism);
    }

    @BeforeClass
    public static void startSSL() throws Exception {
        Assume.assumeTrue("UNDERTOW-2112 New version TLSv1.3 and JDK14 and newer versions are breaking this feature",
                getJavaSpecificationVersion() < 14);
        DefaultServer.startSSLServer(OptionMap.create(SSL_CLIENT_AUTH_MODE, NOT_REQUESTED));
        clientSSLContext = DefaultServer.getClientSSLContext();
    }

    @AfterClass
    public static void stopSSL() throws Exception {
        clientSSLContext = null;
        DefaultServer.stopSSLServer();
    }

    @Test
    public void testClientCertSuccess() throws Exception {
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(clientSSLContext);
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        Header[] values = result.getHeaders("ProcessedBy");
        assertEquals("ProcessedBy Headers", 1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());

        values = result.getHeaders("AuthenticatedUser");
        assertEquals("AuthenticatedUser Headers", 1, values.length);
        assertEquals("CN=Test Client,OU=OU,O=Org,L=City,ST=State,C=GB", values[0].getValue());
        HttpClientUtils.readResponse(result);
        assertSingleNotificationType(EventType.AUTHENTICATED);
    }

    @Test
    public void testClientCertSuccessWithPostBody() throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            client.setSSLContext(clientSSLContext);
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerSSLAddress());
            post.setEntity(new StringEntity("hi"));
            HttpResponse result = client.execute(post);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            Header[] values = result.getHeaders("ProcessedBy");
            assertEquals("ProcessedBy Headers", 1, values.length);
            assertEquals("ResponseHandler", values[0].getValue());

            values = result.getHeaders("AuthenticatedUser");
            assertEquals("AuthenticatedUser Headers", 1, values.length);
            assertEquals("CN=Test Client,OU=OU,O=Org,L=City,ST=State,C=GB", values[0].getValue());
            HttpClientUtils.readResponse(result);
            assertSingleNotificationType(EventType.AUTHENTICATED);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testClientCertSuccessWithLargePostBody() throws Exception {
        PooledByteBuffer buf = DefaultServer.getBufferPool().allocate();
        int requestSize = buf.getBuffer().limit() - 1;
        buf.close();

        final StringBuilder messageBuilder = new StringBuilder(requestSize);
        for (int i = 0; i < requestSize; ++i) {
            messageBuilder.append("*");
        }

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(clientSSLContext);
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerSSLAddress());
        post.setEntity(new StringEntity(messageBuilder.toString()));
        HttpResponse result = client.execute(post);
        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        Header[] values = result.getHeaders("ProcessedBy");
        assertEquals("ProcessedBy Headers", 1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());

        values = result.getHeaders("AuthenticatedUser");
        assertEquals("AuthenticatedUser Headers", 1, values.length);
        assertEquals("CN=Test Client,OU=OU,O=Org,L=City,ST=State,C=GB", values[0].getValue());
        HttpClientUtils.readResponse(result);
        assertSingleNotificationType(EventType.AUTHENTICATED);
    }

    private static int getJavaSpecificationVersion() {
        String versionString = System.getProperty("java.specification.version");
        versionString = versionString.startsWith("1.") ? versionString.substring(2) : versionString;
        return Integer.parseInt(versionString);
    }
}
