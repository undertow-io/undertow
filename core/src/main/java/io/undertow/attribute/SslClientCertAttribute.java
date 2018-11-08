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
 * 返回ssl 中客户端信息
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

        @Override
        public int priority() {
            return 0;
        }
    }
}
