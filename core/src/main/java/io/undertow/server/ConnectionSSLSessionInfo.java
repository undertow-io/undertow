/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server;

import java.io.IOException;
import java.security.cert.Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import io.netty.handler.ssl.ClientAuth;

/**
 * SSL session information that is read directly from the SSL session of the
 * XNIO connection
 *
 * @author Stuart Douglas
 */
public class ConnectionSSLSessionInfo implements SSLSessionInfo {

    private static final SSLPeerUnverifiedException PEER_UNVERIFIED_EXCEPTION = new SSLPeerUnverifiedException("");
    private static final RenegotiationRequiredException RENEGOTIATION_REQUIRED_EXCEPTION = new RenegotiationRequiredException();

    private static final long MAX_RENEGOTIATION_WAIT = 30000;

    private final SSLSession session;
    private SSLPeerUnverifiedException unverified;
    private RenegotiationRequiredException renegotiationRequiredException;

    public ConnectionSSLSessionInfo(SSLSession session) {
        this.session = session;
    }


    @Override
    public byte[] getSessionId() {
        return session.getId();
    }

    @Override
    public String getCipherSuite() {
        return session.getCipherSuite();
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException, RenegotiationRequiredException {
        if (unverified != null) {
            throw unverified;
        }
        if (renegotiationRequiredException != null) {
            throw renegotiationRequiredException;
        }
        try {
            return session.getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
//            try {
//                SslClientAuthMode sslClientAuthMode = channel.getOption(Options.SSL_CLIENT_AUTH_MODE);
//                if (sslClientAuthMode == SslClientAuthMode.NOT_REQUESTED) {
//                    renegotiationRequiredException = RENEGOTIATION_REQUIRED_EXCEPTION;
//                    throw renegotiationRequiredException;
//                }
//            } catch (IOException e1) {
//                //ignore, will not actually happen
//            }
            unverified = PEER_UNVERIFIED_EXCEPTION;
            throw unverified;
        }
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException, RenegotiationRequiredException {
        if (unverified != null) {
            throw unverified;
        }
        if (renegotiationRequiredException != null) {
            throw renegotiationRequiredException;
        }
        try {
            return session.getPeerCertificateChain();
        } catch (SSLPeerUnverifiedException e) {
//            try {
//                SslClientAuthMode sslClientAuthMode = channel.getOption(Options.SSL_CLIENT_AUTH_MODE);
//                if (sslClientAuthMode == SslClientAuthMode.NOT_REQUESTED) {
//                    renegotiationRequiredException = RENEGOTIATION_REQUIRED_EXCEPTION;
//                    throw renegotiationRequiredException;
//                }
//            } catch (IOException e1) {
//                //ignore, will not actually happen
//            }
            unverified = PEER_UNVERIFIED_EXCEPTION;
            throw unverified;
        }
    }


    @Override
    public void renegotiate(HttpServerExchange exchange, ClientAuth sslClientAuthMode) throws IOException {
        //TODO
    }

    @Override
    public SSLSession getSSLSession() {
        return session;
    }

}
