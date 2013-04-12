/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
package io.undertow.security.impl;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.X509CertificateCredential;
import io.undertow.server.HttpServerExchange;

/**
 * The Client Cert based authentication mechanism.
 *
 * When authenticate is called the current request is checked to see if it a SSL request, this is further checked to identify if
 * the client has been verified at the SSL level.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ClientCertAuthenticationMechanism implements AuthenticationMechanism {

    private final String name;

    public ClientCertAuthenticationMechanism() {
        this("CLIENT-CERT");
    }

    public ClientCertAuthenticationMechanism(final String mechanismName) {
        this.name = mechanismName;
    }

    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange, final SecurityContext securityContext) {
        SSLSession sslSession = exchange.getConnection().getSslSession();
        if (sslSession != null) {
            try {
                Certificate[] clientCerts = sslSession.getPeerCertificates();
                if (clientCerts[0] instanceof X509Certificate) {
                    Credential credential = new X509CertificateCredential((X509Certificate) clientCerts[0]);

                    IdentityManager idm = securityContext.getIdentityManager();
                    Account account = idm.verify(credential);
                    if (account != null) {
                        securityContext.authenticationComplete(account, name);
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

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        return new ChallengeResult(false);
    }

}
