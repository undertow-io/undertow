package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RenegotiationRequiredException;
import io.undertow.server.SSLSessionInfo;
import io.undertow.util.Certificates;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.CertificateEncodingException;
import javax.security.cert.X509Certificate;

/**
 * @author Stuart Douglas
 */
public class SslClientCertAttribute implements ExchangeAttribute {

    public static final SslClientCertAttribute INSTANCE = new SslClientCertAttribute();

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        SSLSessionInfo ssl = exchange.getConnection().getSslSessionInfo();
        if(ssl == null) {
            return null;
        }
        X509Certificate[] certificates;
        try {
            certificates = ssl.getPeerCertificateChain();
            if(certificates.length > 0) {
                return Certificates.toPem(certificates[0]);
            }
            return null;
        } catch (SSLPeerUnverifiedException e) {
            return null;
        } catch (CertificateEncodingException e) {
            return null;
        } catch (RenegotiationRequiredException e) {
            return null;
        }
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("SSL Client Cert", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "SSL Client Cert";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals("%{SSL_CLIENT_CERT}")) {
                return INSTANCE;
            }
            return null;
        }
    }
}
