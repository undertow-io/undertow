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
import io.undertow.security.api.SecurityNotification.EventType;
import io.undertow.security.idm.DigestAlgorithm;
import io.undertow.security.impl.AuthenticationInfoToken;
import io.undertow.security.impl.DigestAuthenticationMechanism;
import io.undertow.security.impl.DigestAuthorizationToken;
import io.undertow.security.impl.DigestQop;
import io.undertow.security.impl.DigestWWWAuthenticateToken;
import io.undertow.security.impl.SimpleNonceManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HexConverter;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.DIGEST;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * For Digest authentication we support RFC2617, however this includes a requirement to allow a fall back to RFC2069, this test
 * case is to test the RFC2069 form of Digest authentication.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
public class DigestAuthentication2069TestCase extends AuthenticationTestBase {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final String REALM_NAME = "Digest_Realm";

    @Override
    protected List<AuthenticationMechanism> getTestMechanisms() {
        List<DigestQop> qopList = Collections.emptyList();
        AuthenticationMechanism mechanism = new DigestAuthenticationMechanism(Collections.singletonList(DigestAlgorithm.MD5),
                qopList, REALM_NAME, "/", new SimpleNonceManager());

        return Collections.singletonList(mechanism);
    }

    /**
     * Creates a response value from the supplied parameters.
     *
     * @return The generated Hex encoded MD5 digest based response.
     */
    private String createResponse(final String userName, final String realm, final String password, final String method,
            final String uri, final String nonce) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(userName.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(realm.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(password.getBytes(UTF_8));

        byte[] ha1 = HexConverter.convertToHexBytes(digest.digest());

        digest.update(method.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(uri.getBytes(UTF_8));

        byte[] ha2 = HexConverter.convertToHexBytes(digest.digest());

        digest.update(ha1);
        digest.update((byte) ':');
        digest.update(nonce.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(ha2);

        return HexConverter.convertToHexString(digest.digest());
    }

    /**
     * Test for a successful authentication.
     */
    @Test
    public void testDigestSuccess() throws Exception {
        setAuthenticationChain();

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        String value = values[0].getValue();
        assertTrue(value.startsWith(DIGEST.toString()));
        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));
        assertFalse(parsedHeader.containsKey(DigestWWWAuthenticateToken.MESSAGE_QOP));

        String nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);

        String response = createResponse("userOne", REALM_NAME, "passwordOne", "GET", "/", nonce);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());
        StringBuilder sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"userOne\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\"");

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);
        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());

        values = result.getHeaders("Authentication-Info");
        assertEquals(1, values.length);
        Map<AuthenticationInfoToken, String> parsedAuthInfo = AuthenticationInfoToken.parseHeader(values[0].getValue());

        nonce = parsedAuthInfo.get(AuthenticationInfoToken.NEXT_NONCE);
        response = createResponse("userOne", REALM_NAME, "passwordOne", "GET", "/", nonce);
        assertSingleNotificationType(EventType.AUTHENTICATED);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());
        sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"userOne\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\"");

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);
        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
        assertSingleNotificationType(EventType.AUTHENTICATED);
    }

    /**
     * Test that a request is correctly rejected with a bad user name.
     *
     * In this case both the supplied username is wrong and also the generated response can not be valid as there is no
     * corresponding user.
     */
    @Test
    public void testBadUserName() throws Exception {
        setAuthenticationChain();

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        String value = values[0].getValue();
        assertTrue(value.startsWith(DIGEST.toString()));
        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));

        String nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);

        String response = createResponse("badUser", REALM_NAME, "passwordOne", "GET", "/", nonce);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());
        StringBuilder sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"badUser\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\"");

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        assertSingleNotificationType(EventType.FAILED_AUTHENTICATION);
    }

    /**
     * Test that a request is correctly rejected if a bad password is used to generate the response value.
     */
    @Test
    public void testBadPassword() throws Exception {
        setAuthenticationChain();

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        String value = values[0].getValue();
        assertTrue(value.startsWith(DIGEST.toString()));
        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));

        String nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);

        String response = createResponse("userOne", REALM_NAME, "badPassword", "GET", "/", nonce);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());
        StringBuilder sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"userOne\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\"");

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        assertSingleNotificationType(EventType.FAILED_AUTHENTICATION);
    }

    /**
     * Test that for a valid username and password if an invalid nonce is used the request should be rejected with the nonce
     * marked as stale, using the replacement nonce should then work.
     */
    @Test
    public void testDifferentNonce() throws Exception {
        setAuthenticationChain();

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        String value = values[0].getValue();
        assertTrue(value.startsWith(DIGEST.toString()));
        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));

        String nonce = "AU1aCIiy48ENMTM1MTE3OTUxMDU2OLrHnBlV2GBzzguCWOPET+0=";

        String response = createResponse("userOne", REALM_NAME, "passwordOne", "GET", "/", nonce);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());
        StringBuilder sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"userOne\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\"");

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        value = values[0].getValue();
        assertTrue(value.startsWith(DIGEST.toString()));
        parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));
        assertEquals("true", parsedHeader.get(DigestWWWAuthenticateToken.STALE));

        nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);
        response = createResponse("userOne", REALM_NAME, "passwordOne", "GET", "/", nonce);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());
        sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"userOne\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\"");

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);

        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
        // The additional round trip for the bad nonce should not trigger a security notification.
        assertSingleNotificationType(EventType.AUTHENTICATED);
    }

    /**
     * Test that in RFC2069 mode nonce re-use is rejected.
     */
    @Test
    public void testNonceReUse() throws Exception {
        setAuthenticationChain();

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        String value = values[0].getValue();
        assertTrue(value.startsWith(DIGEST.toString()));
        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));

        String nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);

        String response = createResponse("userOne", REALM_NAME, "passwordOne", "GET", "/", nonce);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());
        StringBuilder sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"userOne\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\"");

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);
        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
        assertSingleNotificationType(EventType.AUTHENTICATED);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());

        values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        value = values[0].getValue();
        assertTrue(value.startsWith(DIGEST.toString()));
        parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));
        assertEquals("true", parsedHeader.get(DigestWWWAuthenticateToken.STALE));

        nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);
        response = createResponse("userOne", REALM_NAME, "passwordOne", "GET", "/", nonce);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());
        sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"userOne\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\"");

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);

        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
        // The additional round trip for the bad nonce should not trigger a security notification.
        assertSingleNotificationType(EventType.AUTHENTICATED);
    }

    // Test choosing different algorithm.
    // Different URI - Test not matching the request as well.
    // Different Method

}
