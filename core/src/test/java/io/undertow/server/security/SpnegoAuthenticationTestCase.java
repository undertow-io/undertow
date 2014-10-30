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

import static io.undertow.server.security.KerberosKDCUtil.login;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.NEGOTIATE;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.GSSAPIServerSubjectFactory;
import io.undertow.security.api.SecurityNotification.EventType;
import io.undertow.security.impl.GSSAPIAuthenticationMechanism;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FlexBase64;
import io.undertow.util.StatusCodes;

import java.security.GeneralSecurityException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.List;

import javax.security.auth.Subject;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A test case to test the SPNEGO authentication mechanism.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
@AjpIgnore(apacheOnly = true, value = "SPNEGO requires a single connection to the server, and apache cannot guarantee that")
public class SpnegoAuthenticationTestCase extends AuthenticationTestBase {

    private static Oid SPNEGO;

    @Override
    protected List<AuthenticationMechanism> getTestMechanisms() {
        AuthenticationMechanism mechanism = new GSSAPIAuthenticationMechanism(new SubjectFactory());

        return Collections.singletonList(mechanism);
    }

    @BeforeClass
    public static void startServers() throws Exception {
        KerberosKDCUtil.startServer();
        SPNEGO = new Oid("1.3.6.1.5.5.2");
    }

    @AfterClass
    public static void stopServers() {

    }

    @Test
    public void testSpnegoSuccess() throws Exception {
        setAuthenticationChain();

        final TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        String header = getAuthHeader(NEGOTIATE, values);
        assertEquals(NEGOTIATE.toString(), header);
        HttpClientUtils.readResponse(result);

        Subject clientSubject = login("jduke", "theduke".toCharArray());

        Subject.doAs(clientSubject, new PrivilegedExceptionAction<Void>() {

            @Override
            public Void run() throws Exception {
                GSSManager gssManager = GSSManager.getInstance();
                GSSName serverName = gssManager.createName("HTTP/" + DefaultServer.getDefaultServerAddress().getHostString(), null);

                GSSContext context = gssManager.createContext(serverName, SPNEGO, null, GSSContext.DEFAULT_LIFETIME);

                byte[] token = new byte[0];

                boolean gotOur200 = false;
                while (!context.isEstablished()) {
                    token = context.initSecContext(token, 0, token.length);

                    if (token != null && token.length > 0) {
                        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
                        get.addHeader(AUTHORIZATION.toString(), NEGOTIATE + " " + FlexBase64.encodeString(token, false));
                        HttpResponse result = client.execute(get);

                        Header[] headers = result.getHeaders(WWW_AUTHENTICATE.toString());
                        if (headers.length > 0) {
                            String header = getAuthHeader(NEGOTIATE, headers);

                            byte[] headerBytes = header.getBytes("UTF-8");
                            token = FlexBase64.decode(headerBytes, NEGOTIATE.toString().length() + 1, headerBytes.length).array();
                        }

                        if (result.getStatusLine().getStatusCode() == StatusCodes.OK) {
                            Header[] values = result.getHeaders("ProcessedBy");
                            assertEquals(1, values.length);
                            assertEquals("ResponseHandler", values[0].getValue());
                            HttpClientUtils.readResponse(result);
                            assertSingleNotificationType(EventType.AUTHENTICATED);
                            gotOur200 = true;
                        } else if (result.getStatusLine().getStatusCode() == StatusCodes.UNAUTHORIZED) {
                            assertTrue("We did get a header.", headers.length > 0);

                            HttpClientUtils.readResponse(result);

                        } else {
                            fail(String.format("Unexpected status code %d", result.getStatusLine().getStatusCode()));
                        }
                    }
                }

                assertTrue(gotOur200);
                assertTrue(context.isEstablished());
                return null;
            }
        });
    }

    private class SubjectFactory implements GSSAPIServerSubjectFactory {

        @Override
        public Subject getSubjectForHost(String hostName) throws GeneralSecurityException {
            return login("HTTP/" + hostName, "servicepwd".toCharArray());
        }

    }

}
