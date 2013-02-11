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

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.X509CertificateCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConcreteIoFuture;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;

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

    public String getName() {
        return name;
    }

    public IoFuture<AuthenticationMechanismOutcome> authenticate(final HttpServerExchange exchange,
            final SecurityContext securityContext, final Executor handOffExecutor) {
        ConcreteIoFuture<AuthenticationMechanismOutcome> result = new ConcreteIoFuture<AuthenticationMechanismOutcome>();

        SSLSession sslSession = exchange.getConnection().getSslSession();
        if (sslSession != null) {
            try {
                Certificate[] clientCerts = sslSession.getPeerCertificates();
                if (clientCerts[0] instanceof X509Certificate) {
                    // Hand off to the executor as now we need an IDM based check.
                    handOffExecutor.execute(new ClientCertRunnable(securityContext, result, (X509Certificate) clientCerts[0]));
                    return result;
                }
            } catch (SSLPeerUnverifiedException e) {
                // No action - this mechanism can not attempt authentication without peer certificates so allow it to drop out
                // to NOT_ATTEMPTED.
            }
        }

        // There was no SSLSession to verify or early verification failed.
        result.setResult(AuthenticationMechanismOutcome.NOT_ATTEMPTED);
        return result;
    }

    private final class ClientCertRunnable implements Runnable {
        private final SecurityContext securityContext;
        private final ConcreteIoFuture<AuthenticationMechanismOutcome> result;
        private final X509Certificate certificate;

        private ClientCertRunnable(SecurityContext securityContext, ConcreteIoFuture<AuthenticationMechanismOutcome> result,
                X509Certificate certificate) {
            this.result = result;
            this.securityContext = securityContext;
            this.certificate = certificate;
        }

        public void run() {
            Credential credential = new X509CertificateCredential(certificate);

            IdentityManager idm = securityContext.getIdentityManager();
            Account account = idm.verify(credential);
            if (account != null) {
                securityContext.authenticationComplete(account, getName(), false);
                result.setResult(AuthenticationMechanismOutcome.AUTHENTICATED);
            } else {
                // TODO - Double check if we want NOT_AUTHENTICATED - this mechanism we may want to fail silently with
                // NOT_ATTEMPTED as triggering a challenge will not help this mechanism and may inadvertently affect the others.
                result.setResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED);
            }
        }
    }

    @Override
    public IoFuture<ChallengeResult> sendChallenge(HttpServerExchange exchange, SecurityContext securityContext,
            Executor handOffExecutor) {
        return new FinishedIoFuture<AuthenticationMechanism.ChallengeResult>(new ChallengeResult(false));
    }

}
