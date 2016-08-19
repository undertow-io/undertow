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
import java.util.Random;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.DIGEST;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test case for Digest authentication based on RFC2617 with QOP of auth.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
public class DigestAuthenticationAuthTestCase extends AuthenticationTestBase {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final String REALM_NAME = "Digest_Realm";
    private static final String ZERO = "00000000";

    static AuthenticationMechanism getTestMechanism() {
        return new DigestAuthenticationMechanism(Collections.singletonList(DigestAlgorithm.MD5),
                Collections.singletonList(DigestQop.AUTH), REALM_NAME, "/", new SimpleNonceManager());
    }

    /**
     * @see io.undertow.server.security.AuthenticationTestBase#getTestMechanisms()
     */
    @Override
    protected List<AuthenticationMechanism> getTestMechanisms() {
        AuthenticationMechanism mechanism = getTestMechanism();

        return Collections.singletonList(mechanism);
    }

    private static String createNonce() {
        // This if just for testing so we are not concerned with how securely the client side nonce is.
        Random rand = new Random();
        byte[] nonceBytes = new byte[32];
        rand.nextBytes(nonceBytes);

        return HexConverter.convertToHexString(nonceBytes);
    }

    /**
     * Creates a response value from the supplied parameters.
     *
     * @return The generated Hex encoded MD5 digest based response.
     */
    private static String createResponse(final String userName, final String realm, final String password, final String method,
            final String uri, final String nonce, final String nonceCount, final String cnonce) throws Exception {
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
        digest.update(nonceCount.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(cnonce.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(DigestQop.AUTH.getToken().getBytes(UTF_8));
        digest.update((byte) ':');

        digest.update(ha2);

        return HexConverter.convertToHexString(digest.digest());
    }

    private static String createRspAuth(final String userName, final String realm, final String password, final String uri,
            final String nonce, final String nonceCount, final String cnonce) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(userName.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(realm.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(password.getBytes(UTF_8));

        byte[] ha1 = HexConverter.convertToHexBytes(digest.digest());

        digest.update((byte) ':');
        digest.update(uri.getBytes(UTF_8));

        byte[] ha2 = HexConverter.convertToHexBytes(digest.digest());

        digest.update(ha1);
        digest.update((byte) ':');
        digest.update(nonce.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(nonceCount.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(cnonce.getBytes(UTF_8));
        digest.update((byte) ':');
        digest.update(DigestQop.AUTH.getToken().getBytes(UTF_8));
        digest.update((byte) ':');

        digest.update(ha2);

        return HexConverter.convertToHexString(digest.digest());
    }

    private static String createAuthorizationLine(final String userName, final String password, final String method, final String uri,
            final String nonce, final int nonceCount, final String cnonce, final String opaque) throws Exception {
        StringBuilder sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"userOne\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        String nonceCountHex = toHex(nonceCount);
        String response = createResponse(userName, REALM_NAME, password, method, uri, nonce, nonceCountHex, cnonce);
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\",");
        sb.append(DigestAuthorizationToken.ALGORITHM.getName()).append("=\"").append(DigestAlgorithm.MD5.getToken())
                .append("\",");
        sb.append(DigestAuthorizationToken.CNONCE.getName()).append("=\"").append(cnonce).append("\",");
        sb.append(DigestAuthorizationToken.OPAQUE.getName()).append("=\"").append(opaque).append("\",");
        sb.append(DigestAuthorizationToken.MESSAGE_QOP.getName()).append("=\"").append(DigestQop.AUTH.getToken()).append("\",");
        sb.append(DigestAuthorizationToken.NONCE_COUNT.getName()).append("=").append(nonceCountHex);

        return sb.toString();
    }

    private static String toHex(final int number) {
        String temp = Integer.toHexString(number);

        return ZERO.substring(temp.length()) + temp;
    }

    /**
     * Test for a successful authentication.
     *
     * Also makes two additional calls to demonstrate nonce re-use with an incrementing nonce count.
     */
    @Test
    public void testDigestSuccess() throws Exception {
        setAuthenticationChain();
        _testDigestSuccess();
    }

    static void _testDigestSuccess() throws Exception {
        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        String value = getAuthHeader(DIGEST, values);

        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));
        assertEquals(DigestQop.AUTH.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.MESSAGE_QOP));

        String clientNonce = createNonce();
        int nonceCount = 1;
        String nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);
        String opaque = parsedHeader.get(DigestWWWAuthenticateToken.OPAQUE);
        assertNotNull(opaque);
        // Send 5 requests with an incrementing nonce count on each call.
        for (int i = 0; i < 5; i++) {
            client = new TestHttpClient();
            get = new HttpGet(DefaultServer.getDefaultServerURL());

            int thisNonceCount = nonceCount++;
            String authorization = createAuthorizationLine("userOne", "passwordOne", "GET", "/", nonce, thisNonceCount,
                    clientNonce, opaque);

            get.addHeader(AUTHORIZATION.toString(), authorization);
            result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            values = result.getHeaders("ProcessedBy");
            assertEquals(1, values.length);
            assertEquals("ResponseHandler", values[0].getValue());
            assertSingleNotificationType(EventType.AUTHENTICATED);

            values = result.getHeaders("Authentication-Info");
            assertEquals(1, values.length);
            Map<AuthenticationInfoToken, String> parsedAuthInfo = AuthenticationInfoToken.parseHeader(values[0].getValue());

            assertEquals("Didn't expect a new nonce.", nonce, parsedAuthInfo.get(AuthenticationInfoToken.NEXT_NONCE));
            assertEquals(DigestQop.AUTH.getToken(), parsedAuthInfo.get(AuthenticationInfoToken.MESSAGE_QOP));
            String nonceCountString = toHex(thisNonceCount);
            assertEquals(createRspAuth("userOne", REALM_NAME, "passwordOne", "/", nonce, nonceCountString, clientNonce),
                    parsedAuthInfo.get(AuthenticationInfoToken.RESPONSE_AUTH));
            assertEquals(clientNonce, parsedAuthInfo.get(AuthenticationInfoToken.CNONCE));
            assertEquals(nonceCountString, parsedAuthInfo.get(AuthenticationInfoToken.NONCE_COUNT));
        }
    }

    /**
     * Test for a failed authentication where a bad username is provided.
     */
    @Test
    public void testBadUsername() throws Exception {
        setAuthenticationChain();
        _testBadUsername();
    }

    static void _testBadUsername() throws Exception {
        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());

        String value = getAuthHeader(DIGEST, values);

        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));
        assertEquals(DigestQop.AUTH.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.MESSAGE_QOP));

        String clientNonce = createNonce();
        int nonceCount = 1;
        String nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);
        String opaque = parsedHeader.get(DigestWWWAuthenticateToken.OPAQUE);
        assertNotNull(opaque);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());

        int thisNonceCount = nonceCount++;
        String authorization = createAuthorizationLine("noUser", "passwordOne", "GET", "/", nonce, thisNonceCount, clientNonce,
                opaque);

        get.addHeader(AUTHORIZATION.toString(), authorization);
        result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        assertSingleNotificationType(EventType.FAILED_AUTHENTICATION);
    }

    /**
     * Test for a failed authentication where a bad password is provided.
     */
    @Test
    public void testBadPassword() throws Exception {
        setAuthenticationChain();
        _testBadPassword();
    }

    static void _testBadPassword() throws Exception {
        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());

        String value = getAuthHeader(DIGEST, values);

        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));
        assertEquals(DigestQop.AUTH.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.MESSAGE_QOP));

        String clientNonce = createNonce();
        int nonceCount = 1;
        String nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);
        String opaque = parsedHeader.get(DigestWWWAuthenticateToken.OPAQUE);
        assertNotNull(opaque);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());

        int thisNonceCount = nonceCount++;
        String authorization = createAuthorizationLine("userOne", "badPassword", "GET", "/", nonce, thisNonceCount,
                clientNonce, opaque);

        get.addHeader(AUTHORIZATION.toString(), authorization);
        result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        assertSingleNotificationType(EventType.FAILED_AUTHENTICATION);
    }

    /**
     * Test for a failed authentication where a bad nonce is provided.
     */
    @Test
    public void testBadNonce() throws Exception {
        setAuthenticationChain();
        _testBadNonce();
    }

    static void _testBadNonce() throws Exception {
        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());

        String value = getAuthHeader(DIGEST, values);

        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));
        assertEquals(DigestQop.AUTH.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.MESSAGE_QOP));

        String clientNonce = createNonce();
        int nonceCount = 1;
        String nonce = "AU1aCIiy48ENMTM1MTE3OTUxMDU2OLrHnBlV2GBzzguCWOPET+0=";
        String opaque = parsedHeader.get(DigestWWWAuthenticateToken.OPAQUE);
        assertNotNull(opaque);

        client = new TestHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerURL());

        int thisNonceCount = nonceCount++;
        String authorization = createAuthorizationLine("userOne", "badPassword", "GET", "/", nonce, thisNonceCount,
                clientNonce, opaque);

        get.addHeader(AUTHORIZATION.toString(), authorization);
        result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        assertSingleNotificationType(EventType.FAILED_AUTHENTICATION);
    }

    /**
     * Test for a failed authentication where the nonce count is re-used.
     *
     * Where a nonce count is used the nonce can now be re-used, however each time the nonce count must be different.
     */
    @Test
    public void testNonceCountReUse() throws Exception {
        setAuthenticationChain();
        _testNonceCountReUse();
    }

    static void _testNonceCountReUse() throws Exception {
        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());

        String value = getAuthHeader(DIGEST, values);

        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));
        assertEquals(DigestQop.AUTH.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.MESSAGE_QOP));

        String clientNonce = createNonce();
        int nonceCount = 1;
        String nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);
        String opaque = parsedHeader.get(DigestWWWAuthenticateToken.OPAQUE);
        assertNotNull(opaque);
        // Send 5 requests with an incrementing nonce count on each call.
        for (int i = 0; i < 2; i++) {
            client = new TestHttpClient();
            get = new HttpGet(DefaultServer.getDefaultServerURL());

            int thisNonceCount = nonceCount; // Note - No increment
            String authorization = createAuthorizationLine("userOne", "passwordOne", "GET", "/", nonce, thisNonceCount,
                    clientNonce, opaque);

            get.addHeader(AUTHORIZATION.toString(), authorization);
            result = client.execute(get);

            if (i == 0) {
                assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                assertSingleNotificationType(EventType.AUTHENTICATED);

                values = result.getHeaders("ProcessedBy");
                assertEquals(1, values.length);
                assertEquals("ResponseHandler", values[0].getValue());

                values = result.getHeaders("Authentication-Info");
                assertEquals(1, values.length);
                Map<AuthenticationInfoToken, String> parsedAuthInfo = AuthenticationInfoToken.parseHeader(values[0].getValue());

                assertEquals("Didn't expect a new nonce.", nonce, parsedAuthInfo.get(AuthenticationInfoToken.NEXT_NONCE));
                assertEquals(DigestQop.AUTH.getToken(), parsedAuthInfo.get(AuthenticationInfoToken.MESSAGE_QOP));
                String nonceCountString = toHex(thisNonceCount);
                assertEquals(createRspAuth("userOne", REALM_NAME, "passwordOne", "/", nonce, nonceCountString, clientNonce),
                        parsedAuthInfo.get(AuthenticationInfoToken.RESPONSE_AUTH));
                assertEquals(clientNonce, parsedAuthInfo.get(AuthenticationInfoToken.CNONCE));
                assertEquals(nonceCountString, parsedAuthInfo.get(AuthenticationInfoToken.NONCE_COUNT));
            } else {
                assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
            }
        }
    }

}
