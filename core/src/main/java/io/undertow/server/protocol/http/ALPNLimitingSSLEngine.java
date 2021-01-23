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

package io.undertow.server.protocol.http;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/**
 * SSLEngine that will limit the cipher selection to HTTP/2 suitable protocols if the client is offering h2 as an option.
 * <p>
 * In theory this is not a perfect solution to the HTTP/2 cipher strength issue, but in practice it should be sufficient
 * as any RFC compliant implementation should be able to negotiate TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
 *
 * @author Stuart Douglas
 */
public class ALPNLimitingSSLEngine extends SSLEngine {
    private static final SSLEngineResult UNDERFLOW_RESULT = new SSLEngineResult(
            SSLEngineResult.Status.BUFFER_UNDERFLOW, SSLEngineResult.HandshakeStatus.NEED_UNWRAP, 0, 0);

    private final SSLEngine delegate;
    private final Runnable invalidAlpnRunnable;
    private boolean done;

    public ALPNLimitingSSLEngine(SSLEngine delegate, Runnable invalidAlpnRunnable) {
        this.delegate = delegate;
        this.invalidAlpnRunnable = invalidAlpnRunnable;
    }

    @Override
    public String getPeerHost() {
        return delegate.getPeerHost();
    }

    @Override
    public int getPeerPort() {
        return delegate.getPeerPort();
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return delegate.wrap(src, dst);
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] srcs, ByteBuffer dst) throws SSLException {
        return wrap(srcs, 0, srcs.length, dst);
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        if (done) {
            return delegate.unwrap(src, dst);
        }
        if (ALPNOfferedClientHelloExplorer.isIncompleteHeader(src)) {
            return UNDERFLOW_RESULT;
        }
        try {
            List<Integer> clientCiphers = ALPNOfferedClientHelloExplorer.parseClientHello(src);
            if (clientCiphers != null) {
                limitCiphers(clientCiphers);
                done = true;
            } else {
                done = true;
            }
        } catch (BufferUnderflowException e) {
            return UNDERFLOW_RESULT;
        }
        return delegate.unwrap(src, dst);
    }

    private void limitCiphers(List<Integer> clientCiphers) {
        boolean clientIsCompliant = false;
        for (int cipher : clientCiphers) {
            if (cipher == 0xC02F) { //TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256, required to be offered by spec
                clientIsCompliant = true;
            }
        }
        if (!clientIsCompliant) {
            invalidAlpnRunnable.run();
        } else {
            List<String> ciphers = new ArrayList<>();
            for (String cipher : delegate.getEnabledCipherSuites()) {
                if (ALPNBannedCiphers.isAllowed(cipher)) {
                    ciphers.add(cipher);
                }
            }
            delegate.setEnabledCipherSuites(ciphers.toArray(new String[ciphers.size()]));
        }
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts) throws SSLException {
        return unwrap(src, dsts, 0, dsts.length);
    }

    @Override
    public SSLSession getHandshakeSession() {
        return delegate.getHandshakeSession();
    }

    @Override
    public SSLParameters getSSLParameters() {
        return delegate.getSSLParameters();
    }

    @Override
    public void setSSLParameters(final SSLParameters sslParameters) {
        delegate.setSSLParameters(sslParameters);
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] srcs, int off, int len, ByteBuffer dst) throws SSLException {
        return delegate.wrap(srcs, off, len, dst);
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer byteBuffer, ByteBuffer[] byteBuffers, int i, int i1) throws SSLException {
        if (done) {
            return delegate.unwrap(byteBuffer, byteBuffers, i, i1);
        }

        if (ALPNOfferedClientHelloExplorer.isIncompleteHeader(byteBuffer)) {
            return UNDERFLOW_RESULT;
        }
        try {
            List<Integer> clientCiphers = ALPNOfferedClientHelloExplorer.parseClientHello(byteBuffer);
            if (clientCiphers != null) {
                limitCiphers(clientCiphers);
                done = true;
            } else {
                done = true;
            }
        } catch (BufferUnderflowException e) {
            return UNDERFLOW_RESULT;
        }
        return delegate.unwrap(byteBuffer, byteBuffers, i, i1);
    }

    @Override
    public Runnable getDelegatedTask() {
        return delegate.getDelegatedTask();
    }

    @Override
    public void closeInbound() throws SSLException {
        delegate.closeInbound();
    }

    @Override
    public boolean isInboundDone() {
        return delegate.isInboundDone();
    }

    @Override
    public void closeOutbound() {
        delegate.closeOutbound();
    }

    @Override
    public boolean isOutboundDone() {
        return delegate.isOutboundDone();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return delegate.getEnabledCipherSuites();
    }

    @Override
    public void setEnabledCipherSuites(final String[] strings) {
        delegate.setEnabledCipherSuites(strings);
    }

    @Override
    public String[] getSupportedProtocols() {
        return delegate.getSupportedProtocols();
    }

    @Override
    public String[] getEnabledProtocols() {
        return delegate.getEnabledProtocols();
    }

    @Override
    public void setEnabledProtocols(final String[] strings) {
        delegate.setEnabledProtocols(strings);
    }

    @Override
    public SSLSession getSession() {
        return delegate.getSession();
    }

    @Override
    public void beginHandshake() throws SSLException {
        delegate.beginHandshake();
    }

    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return delegate.getHandshakeStatus();
    }

    @Override
    public void setUseClientMode(boolean b) {
        if (b) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public boolean getUseClientMode() {
        return delegate.getUseClientMode();
    }

    @Override
    public void setNeedClientAuth(final boolean b) {
        delegate.setNeedClientAuth(b);
    }

    @Override
    public boolean getNeedClientAuth() {
        return delegate.getNeedClientAuth();
    }

    @Override
    public void setWantClientAuth(final boolean b) {
        delegate.setWantClientAuth(b);
    }

    @Override
    public boolean getWantClientAuth() {
        return delegate.getWantClientAuth();
    }

    @Override
    public void setEnableSessionCreation(final boolean b) {
        delegate.setEnableSessionCreation(b);
    }

    @Override
    public boolean getEnableSessionCreation() {
        return delegate.getEnableSessionCreation();
    }
}
