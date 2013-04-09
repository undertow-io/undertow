package io.undertow.servlet.handlers.security;

import javax.net.ssl.SSLSession;
import javax.servlet.ServletRequest;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpHandlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;

/**
 * Handler that associates SSL metadata with request
 * <p/>
 * cipher suite - javax.servlet.request.cipher_suite String
 * bit size of the algorithm - javax.servlet.request.key_size Integer
 * SSL session id - javax.servlet.request.ssl_session_id String
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public class SSLInformationAssociationHandler implements HttpHandler {
    private final HttpHandler next;

    public SSLInformationAssociationHandler(final HttpHandler next) {
        this.next = next;
    }

    /**
     * Given the name of a TLS/SSL cipher suite, return an int representing it effective stream
     * cipher key strength. i.e. How much entropy material is in the key material being fed into the
     * encryption routines.
     * <p>
     * http://www.thesprawl.org/research/tls-and-ssl-cipher-suites/
     * </p>
     *
     * @param cipherSuite String name of the TLS cipher suite.
     * @return int indicating the effective key entropy bit-length.
     */
    public static int getKeyLenght(String cipherSuite) {
        // Roughly ordered from most common to least common.
        if (cipherSuite == null) {
            return 0;
        } else if (cipherSuite.contains("WITH_AES_256_")) {
            return 256;
        } else if (cipherSuite.contains("WITH_RC4_128_")) {
            return 128;
        } else if (cipherSuite.contains("WITH_AES_128_")) {
            return 128;
        } else if (cipherSuite.contains("WITH_RC4_40_")) {
            return 40;
        } else if (cipherSuite.contains("WITH_3DES_EDE_CBC_")) {
            return 168;
        } else if (cipherSuite.contains("WITH_IDEA_CBC_")) {
            return 128;
        } else if (cipherSuite.contains("WITH_RC2_CBC_40_")) {
            return 40;
        } else if (cipherSuite.contains("WITH_DES40_CBC_")) {
            return 40;
        } else if (cipherSuite.contains("WITH_DES_CBC_")) {
            return 56;
        } else {
            return 0;
        }
    }


     /* ------------------------------------------------------------ */

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        ServletRequest request = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        SSLSession ssl = exchange.getConnection().getSslSession();
        if (ssl != null) {
            request.setAttribute("javax.servlet.request.cipher_suite", ssl.getCipherSuite());
            request.setAttribute("javax.servlet.request.key_size", getKeyLenght(ssl.getCipherSuite()));
            request.setAttribute("javax.servlet.request.ssl_session_id", ssl.getId());
            if (ssl.getPeerCertificateChain() != null) {
                request.setAttribute("javax.servlet.request.X509Certificate", ssl.getPeerCertificateChain());
            }
        }
        HttpHandlers.executeHandler(next, exchange);
    }
}
