/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
package io.undertow.test.handlers.security;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.DIGEST;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.undertow.server.handlers.security.AuthenticationMechanism;
import io.undertow.server.handlers.security.DigestAlgorithm;
import io.undertow.server.handlers.security.DigestAuthenticationMechanism;
import io.undertow.server.handlers.security.DigestAuthorizationToken;
import io.undertow.server.handlers.security.DigestQop;
import io.undertow.server.handlers.security.DigestWWWAuthenticateToken;
import io.undertow.server.handlers.security.HexConverter;
import io.undertow.server.handlers.security.SimpleNonceManager;
import io.undertow.test.utils.DefaultServer;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * For Digest authentication we support RFC2617, however this includes a requirement to allow a fall back to RFC2069, this test
 * case is to test the RFC2069 form of Digest authentication.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
public class DigestAuthentication2069TestCase extends UsernamePasswordAuthenticationTestBase {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String REALM_NAME = "Digest_Realm";

    @Override
    protected AuthenticationMechanism getTestMechanism() {
        List<DigestQop> qopList = Collections.emptyList();
        return new DigestAuthenticationMechanism(Collections.singletonList(DigestAlgorithm.MD5), qopList, REALM_NAME,
                callbackHandler, new SimpleNonceManager());
    }

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

    @Test
    public void testDigestSuccess() throws Exception {
        setAuthenticationChain();

        DefaultHttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress());
        HttpResponse result = client.execute(get);
        assertEquals(401, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        String value = values[0].getValue();
        assertTrue(value.startsWith(DIGEST.toString()));
        Map<DigestWWWAuthenticateToken, String> parsedHeader = DigestWWWAuthenticateToken.parseHeader(value.substring(7));
        assertEquals(REALM_NAME, parsedHeader.get(DigestWWWAuthenticateToken.REALM));
        assertEquals(DigestAlgorithm.MD5.getToken(), parsedHeader.get(DigestWWWAuthenticateToken.ALGORITHM));

        String nonce = parsedHeader.get(DigestWWWAuthenticateToken.NONCE);

        String response = createResponse("userOne", REALM_NAME, "passwordOne", "GET", "/", nonce);

        client = new DefaultHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerAddress());
        StringBuilder sb = new StringBuilder(DIGEST.toString());
        sb.append(" ");
        sb.append(DigestAuthorizationToken.USERNAME.getName()).append("=").append("\"userOne\"").append(",");
        sb.append(DigestAuthorizationToken.REALM.getName()).append("=\"").append(REALM_NAME).append("\",");
        sb.append(DigestAuthorizationToken.NONCE.getName()).append("=\"").append(nonce).append("\",");
        sb.append(DigestAuthorizationToken.DIGEST_URI.getName()).append("=\"/\",");
        sb.append(DigestAuthorizationToken.RESPONSE.getName()).append("=\"").append(response).append("\"");

        get.addHeader(AUTHORIZATION.toString(), sb.toString());
        result = client.execute(get);
        assertEquals(200, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
    }

    // Test bad user name.
    // Test bad password.
    // Test completely different nonce.
    // Test nonce re-use.
    // Test choosing different algorithm.
    // Different URI
    // Different Method

}
