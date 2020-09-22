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
package io.undertow.servlet.test.security.ssl;

import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.SSLHeaderHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.util.Headers;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test SSLMetaData but emulating the proxy headers. This way some extra
 * specific tests sending crafted headers can be performed. Only needed in
 * normal execution, not needed in proxy profiles.
 *
 * @author rmartinc
 */
@ProxyIgnore
@RunWith(DefaultServer.class)
public class SSLMetaDataProxyTestCase extends SSLMetaDataTestCase {

    private static final String DUMMY_KEYSTORE = "dummy.keystore";
    private static final String DUMMY_PASSWORD = "password";
    private static X509Certificate dummyCertificate = null;

    @BeforeClass
    public static void setup() throws Exception {
        final PathHandler root = setupPathHandler();

        // force the setup of the SSLHeaderHandler to add the SSL headers
        SSLHeaderHandler sslHeaders = new SSLHeaderHandler(root);
        DefaultServer.setRootHandler(sslHeaders);

        final InputStream stream = SSLMetaDataTestCase.class.getClassLoader().getResourceAsStream(DUMMY_KEYSTORE);
        KeyStore dummyKeystore = KeyStore.getInstance("JKS");
        dummyKeystore.load(stream, DUMMY_PASSWORD.toCharArray());
        dummyCertificate = (X509Certificate) dummyKeystore.getCertificate("dummy");
    }

    @Test
    public void testWithHeaders() throws Exception {
        Base64.Encoder encoder = Base64.getMimeEncoder();
        String cert = "-----BEGIN CERTIFICATE----- " + encoder.encodeToString(dummyCertificate.getEncoded()) + " -----END CERTIFICATE-----";
        cert = cert.replace("\r\n", " ");
        String id = "1633d36df6f28e1325912b46f7d214f97370c39a6b3fc24ee374a76b3f9b0fba";
        String cipher = "ECDHE-RSA-AES128-GCM-SHA256";
        String keySize = "128";
        Header[] headers = {
            new BasicHeader(Headers.SSL_SESSION_ID_STRING, id),
            new BasicHeader(Headers.SSL_CLIENT_CERT_STRING, cert),
            new BasicHeader(Headers.SSL_CIPHER_STRING, cipher),
            new BasicHeader(Headers.SSL_CIPHER_USEKEYSIZE_STRING, keySize)
        };
        String response = internalTest("/cert-dn", headers);
        Assert.assertEquals(dummyCertificate.getSubjectDN().toString(), response);
        response = internalTest("/id", headers);
        Assert.assertEquals(id, response);
        response = internalTest("/cipher-suite", headers);
        Assert.assertEquals(cipher, response);
        response = internalTest("/key-size", headers);
        Assert.assertEquals(keySize, response);
    }

    @Test
    public void testNoCertWithHeaders() throws Exception {
        String cert = "(null)";
        String id = "1633d36df6f28e1325912b46f7d214f97370c39a6b3fc24ee374a76b3f9b0fba";
        String cipher = "ECDHE-RSA-AES128-GCM-SHA256";
        Header[] headers = {
            new BasicHeader(Headers.SSL_SESSION_ID_STRING, id),
            new BasicHeader(Headers.SSL_CLIENT_CERT_STRING, cert),
            new BasicHeader(Headers.SSL_CIPHER_STRING, cipher)
        };
        String response = internalTest("/cert-dn", headers);
        Assert.assertEquals("null", response);
        response = internalTest("/id", headers);
        Assert.assertEquals(id, response);
        response = internalTest("/cipher-suite", headers);
        Assert.assertEquals(cipher, response);
        response = internalTest("/key-size", headers);
        Assert.assertEquals("0", response);
    }
}
