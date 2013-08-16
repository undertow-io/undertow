package io.undertow.util;

import sun.security.provider.X509Factory;

import javax.security.cert.CertificateEncodingException;
import javax.security.cert.X509Certificate;

/**
 * Utility class for dealing with certificates
 *
 * @author Stuart Douglas
 */
public class Certificates {

    public static String toPem(final X509Certificate certificate) throws CertificateEncodingException {
        final StringBuilder builder = new StringBuilder();
        builder.append(X509Factory.BEGIN_CERT);
        builder.append('\n');
        builder.append(FlexBase64.encodeString(certificate.getEncoded(), true));
        builder.append('\n');
        builder.append(X509Factory.END_CERT);
        return builder.toString();
    }

    private Certificates() {

    }
}
