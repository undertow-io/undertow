package io.undertow.util;

import javax.security.cert.CertificateEncodingException;
import javax.security.cert.X509Certificate;

/**
 * Utility class for dealing with certificates
 *
 * @author Stuart Douglas
 */
public class Certificates {
    public static final java.lang.String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";

    public static final java.lang.String END_CERT = "-----END CERTIFICATE-----";

    public static String toPem(final X509Certificate certificate) throws CertificateEncodingException {
        final StringBuilder builder = new StringBuilder();
        builder.append(BEGIN_CERT);
        builder.append('\n');
        builder.append(FlexBase64.encodeString(certificate.getEncoded(), true));
        builder.append('\n');
        builder.append(END_CERT);
        return builder.toString();
    }

    private Certificates() {

    }
}
