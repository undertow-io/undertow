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
package io.undertow.security.impl;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.X509CertificateCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RenegotiationRequiredException;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.handlers.form.FormParserFactory;

import org.xnio.SslClientAuthMode;

import javax.net.ssl.SSLPeerUnverifiedException;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * The Client Cert based authentication mechanism.
 * <p>
 * When authenticate is called the current request is checked to see if it a SSL request, this is further checked to identify if
 * the client has been verified at the SSL level.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ClientCertAuthenticationMechanism implements AuthenticationMechanism {

    public static final AuthenticationMechanismFactory FACTORY = new Factory();

    public static final String FORCE_RENEGOTIATION = "force_renegotiation";

    private final String name;
    private final IdentityManager identityManager;

    /**
     * If we should force a renegotiation if client certs were not supplied. <code>true</code> by default
     */
    private final boolean forceRenegotiation;

    public ClientCertAuthenticationMechanism() {
        this(true);
    }

    public ClientCertAuthenticationMechanism(boolean forceRenegotiation) {
        this("CLIENT_CERT", forceRenegotiation);
    }

    public ClientCertAuthenticationMechanism(final String mechanismName) {
        this(mechanismName, true);
    }

    public ClientCertAuthenticationMechanism(final String mechanismName, boolean forceRenegotiation) {
        this(mechanismName, forceRenegotiation, null);
    }

    public ClientCertAuthenticationMechanism(final String mechanismName, boolean forceRenegotiation, IdentityManager identityManager) {
        this.name = mechanismName;
        this.forceRenegotiation = forceRenegotiation;
        this.identityManager = identityManager;
    }

    @SuppressWarnings("deprecation")
    private IdentityManager getIdentityManager(SecurityContext securityContext) {
        return identityManager != null ? identityManager : securityContext.getIdentityManager();
    }

    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange, final SecurityContext securityContext) {
        SSLSessionInfo sslSession = exchange.getConnection().getSslSessionInfo();
        if (sslSession != null) {
            try {
                Certificate[] clientCerts = getPeerCertificates(exchange, sslSession, securityContext);
                if (clientCerts[0] instanceof X509Certificate) {
                    Credential credential = new X509CertificateCredential((X509Certificate) clientCerts[0]);

                    IdentityManager idm = getIdentityManager(securityContext);
                    Account account = idm.verify(credential);
                    if (account != null) {
                        securityContext.authenticationComplete(account, name, false);
                        return AuthenticationMechanismOutcome.AUTHENTICATED;
                    }
                }
            } catch (SSLPeerUnverifiedException e) {
                // No action - this mechanism can not attempt authentication without peer certificates so allow it to drop out
                // to NOT_ATTEMPTED.
            }
        }

        /*
         * For ClientCert we do not have a concept of a failed authentication, if the client did use a key then it was deemed
         * acceptable for the connection to be established, this mechanism then just 'attempts' to use it for authentication but
         * does not mandate success.
         */

        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    private Certificate[] getPeerCertificates(final HttpServerExchange exchange, SSLSessionInfo sslSession, SecurityContext securityContext) throws SSLPeerUnverifiedException {
        try {
            return sslSession.getPeerCertificates();
        } catch (RenegotiationRequiredException e) {
            //we only renegotiate if authentication is required
            if (forceRenegotiation && securityContext.isAuthenticationRequired()) {
                try {
                    sslSession.renegotiate(exchange, SslClientAuthMode.REQUESTED);
                    return sslSession.getPeerCertificates();

                } catch (IOException e1) {
                    //ignore
                } catch (RenegotiationRequiredException e1) {
                    //ignore
                }
            }
        }
        throw new SSLPeerUnverifiedException("");
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        return ChallengeResult.NOT_SENT;
    }

    public static final class Factory implements AuthenticationMechanismFactory {

        @Deprecated
        public Factory(IdentityManager identityManager) {}

        public Factory() {}

        @Override
        public AuthenticationMechanism create(String mechanismName,IdentityManager identityManager, FormParserFactory formParserFactory, Map<String, String> properties) {
            String forceRenegotiation = properties.get(FORCE_RENEGOTIATION);
            return new ClientCertAuthenticationMechanism(mechanismName, forceRenegotiation == null ? true : "true".equals(forceRenegotiation), identityManager);
        }
    }

}
