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
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.security.auth.Subject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static io.undertow.server.security.KerberosKDCUtil.login;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.NEGOTIATE;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            client.execute(get, result -> {
                assertEquals(StatusCodes.UNAUTHORIZED, result.getCode());
                Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
                String header = getAuthHeader(NEGOTIATE, values);
                assertEquals(NEGOTIATE.toString(), header);
                return HttpClientUtils.readResponse(result);
            });

            Subject clientSubject = login("jduke", "theduke".toCharArray());

            Subject.doAs(clientSubject, (PrivilegedExceptionAction<Void>) () -> {
                GSSManager gssManager = GSSManager.getInstance();
                GSSName serverName = gssManager.createName("HTTP/" + DefaultServer.getDefaultServerAddress().getHostString(), null);

                GSSContext context = gssManager.createContext(serverName, SPNEGO, null, GSSContext.DEFAULT_LIFETIME);

                byte[] token = new byte[0];
                var ref = new Object() {
                    boolean gotOur200 = false;
                };

                while (!context.isEstablished()) {
                    token = context.initSecContext(token, 0, token.length);

                    if (token != null && token.length > 0) {
                        HttpGet get1 = new HttpGet(DefaultServer.getDefaultServerURL());
                        get1.addHeader(AUTHORIZATION.toString(), NEGOTIATE + " " + FlexBase64.encodeString(token, false));
                        Header[] headers = client.execute(get1, result -> {
                            Header[] authHeaders = result.getHeaders(WWW_AUTHENTICATE.toString());
                            if (result.getCode() == StatusCodes.OK) {
                                Header[] values = result.getHeaders("ProcessedBy");
                                assertEquals(1, values.length);
                                assertEquals("ResponseHandler", values[0].getValue());
                                HttpClientUtils.readResponse(result);
                                assertSingleNotificationType(EventType.AUTHENTICATED);
                                ref.gotOur200 = true;
                            } else if (result.getCode() == StatusCodes.UNAUTHORIZED) {
                                assertTrue("We did get a header.", authHeaders.length > 0);
                                HttpClientUtils.readResponse(result);
                            } else {
                                fail(String.format("Unexpected status code %d", result.getCode()));
                            }
                            return authHeaders;
                        });
                        if (headers.length > 0) {
                            String header = getAuthHeader(NEGOTIATE, headers);

                            byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
                            // FlexBase64.decode() returns byte buffer, which can contain backend array of greater size.
                            // when on such ByteBuffer is called array(), it returns the underlying byte array including the 0 bytes
                            // at the end, which makes the token invalid. => using Base64 mime decoder, which returnes directly properly sized byte[].
                            token = Base64.getMimeDecoder().decode(Arrays.copyOfRange(headerBytes, NEGOTIATE.toString().length() + 1, headerBytes.length));
                        }
                    }
                }

                assertTrue(ref.gotOur200);
                assertTrue(context.isEstablished());
                return null;
            });
        }
    }

    private class SubjectFactory implements GSSAPIServerSubjectFactory {

        @Override
        public Subject getSubjectForHost(String hostName) throws GeneralSecurityException {
            return login("HTTP/" + hostName, "servicepwd".toCharArray());
        }

    }

}
