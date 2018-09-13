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

package io.undertow.protocols.ssl;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;

import io.undertow.UndertowMessages;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class SNISSLEngine extends SSLEngine {

    private static final SSLEngineResult UNDERFLOW_UNWRAP = new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, SSLEngineResult.HandshakeStatus.NEED_UNWRAP, 0, 0);
    private static final SSLEngineResult OK_UNWRAP = new SSLEngineResult(SSLEngineResult.Status.OK, SSLEngineResult.HandshakeStatus.NEED_UNWRAP, 0, 0);
    private final AtomicReference<SSLEngine> currentRef;
    private Function<SSLEngine, SSLEngine> selectionCallback = Function.identity();

    SNISSLEngine(final SNIContextMatcher selector) {
        currentRef = new AtomicReference<>(new InitialState(selector, SSLContext::createSSLEngine));
    }

    SNISSLEngine(final SNIContextMatcher selector, final String host, final int port) {
        super(host, port);
        currentRef = new AtomicReference<>(new InitialState(selector, sslContext -> sslContext.createSSLEngine(host, port)));
    }

    public Function<SSLEngine, SSLEngine> getSelectionCallback() {
        return selectionCallback;
    }

    public void setSelectionCallback(Function<SSLEngine, SSLEngine> selectionCallback) {
        this.selectionCallback = selectionCallback;
    }

    public SSLEngineResult wrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dst) throws SSLException {
        return currentRef.get().wrap(srcs, offset, length, dst);
    }

    public SSLEngineResult wrap(final ByteBuffer src, final ByteBuffer dst) throws SSLException {
        return currentRef.get().wrap(src, dst);
    }

    public SSLEngineResult wrap(final ByteBuffer[] srcs, final ByteBuffer dst) throws SSLException {
        return currentRef.get().wrap(srcs, dst);
    }

    public SSLEngineResult unwrap(final ByteBuffer src, final ByteBuffer[] dsts, final int offset, final int length) throws SSLException {
        return currentRef.get().unwrap(src, dsts, offset, length);
    }

    public SSLEngineResult unwrap(final ByteBuffer src, final ByteBuffer dst) throws SSLException {
        return currentRef.get().unwrap(src, dst);
    }

    public SSLEngineResult unwrap(final ByteBuffer src, final ByteBuffer[] dsts) throws SSLException {
        return currentRef.get().unwrap(src, dsts);
    }

    public String getPeerHost() {
        return currentRef.get().getPeerHost();
    }

    public int getPeerPort() {
        return currentRef.get().getPeerPort();
    }

    public SSLSession getHandshakeSession() {
        return currentRef.get().getHandshakeSession();
    }

    public SSLParameters getSSLParameters() {
        return currentRef.get().getSSLParameters();
    }

    public void setSSLParameters(final SSLParameters params) {
        currentRef.get().setSSLParameters(params);
    }

    public Runnable getDelegatedTask() {
        return currentRef.get().getDelegatedTask();
    }

    public void closeInbound() throws SSLException {
        currentRef.get().closeInbound();
    }

    public boolean isInboundDone() {
        return currentRef.get().isInboundDone();
    }

    public void closeOutbound() {
        currentRef.get().closeOutbound();
    }

    public boolean isOutboundDone() {
        return currentRef.get().isOutboundDone();
    }

    public String[] getSupportedCipherSuites() {
        return currentRef.get().getSupportedCipherSuites();
    }

    public String[] getEnabledCipherSuites() {
        return currentRef.get().getEnabledCipherSuites();
    }

    public void setEnabledCipherSuites(final String[] cipherSuites) {
        currentRef.get().setEnabledCipherSuites(cipherSuites);
    }

    public String[] getSupportedProtocols() {
        return currentRef.get().getSupportedProtocols();
    }

    public String[] getEnabledProtocols() {
        return currentRef.get().getEnabledProtocols();
    }

    public void setEnabledProtocols(final String[] protocols) {
        currentRef.get().setEnabledProtocols(protocols);
    }

    public SSLSession getSession() {
        return currentRef.get().getSession();
    }

    public void beginHandshake() throws SSLException {
        currentRef.get().beginHandshake();
    }

    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return currentRef.get().getHandshakeStatus();
    }

    public void setUseClientMode(final boolean clientMode) {
        currentRef.get().setUseClientMode(clientMode);
    }

    public boolean getUseClientMode() {
        return currentRef.get().getUseClientMode();
    }

    public void setNeedClientAuth(final boolean clientAuth) {
        currentRef.get().setNeedClientAuth(clientAuth);
    }

    public boolean getNeedClientAuth() {
        return currentRef.get().getNeedClientAuth();
    }

    public void setWantClientAuth(final boolean want) {
        currentRef.get().setWantClientAuth(want);
    }

    public boolean getWantClientAuth() {
        return currentRef.get().getWantClientAuth();
    }

    public void setEnableSessionCreation(final boolean flag) {
        currentRef.get().setEnableSessionCreation(flag);
    }

    public boolean getEnableSessionCreation() {
        return currentRef.get().getEnableSessionCreation();
    }

    static final int FL_WANT_C_AUTH = 1 << 0;
    static final int FL_NEED_C_AUTH = 1 << 1;
    static final int FL_SESSION_CRE = 1 << 2;

    class InitialState extends SSLEngine {

        private final SNIContextMatcher selector;
        private final AtomicInteger flags = new AtomicInteger(FL_SESSION_CRE);
        private final Function<SSLContext, SSLEngine> engineFunction;
        private int packetBufferSize = SNISSLExplorer.RECORD_HEADER_SIZE;
        private String[] enabledSuites;
        private String[] enabledProtocols;

        private final SSLSession handshakeSession = new SSLSession() {
            public byte[] getId() {
                throw new UnsupportedOperationException();
            }

            public SSLSessionContext getSessionContext() {
                throw new UnsupportedOperationException();
            }

            public long getCreationTime() {
                throw new UnsupportedOperationException();
            }

            public long getLastAccessedTime() {
                throw new UnsupportedOperationException();
            }

            public void invalidate() {
                throw new UnsupportedOperationException();
            }

            public boolean isValid() {
                return false;
            }

            public void putValue(final String s, final Object o) {
                throw new UnsupportedOperationException();
            }

            public Object getValue(final String s) {
                return null;
            }

            public void removeValue(final String s) {
            }

            public String[] getValueNames() {
                throw new UnsupportedOperationException();
            }

            public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
                throw new UnsupportedOperationException();
            }

            public Certificate[] getLocalCertificates() {
                return null;
            }

            public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
                throw new UnsupportedOperationException();
            }

            public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
                throw new UnsupportedOperationException();
            }

            public Principal getLocalPrincipal() {
                throw new UnsupportedOperationException();
            }

            public String getCipherSuite() {
                throw new UnsupportedOperationException();
            }

            public String getProtocol() {
                throw new UnsupportedOperationException();
            }

            public String getPeerHost() {
                return SNISSLEngine.this.getPeerHost();
            }

            public int getPeerPort() {
                return SNISSLEngine.this.getPeerPort();
            }

            public int getPacketBufferSize() {
                return packetBufferSize;
            }

            public int getApplicationBufferSize() {
                throw new UnsupportedOperationException();
            }
        };

        InitialState(final SNIContextMatcher selector, final Function<SSLContext, SSLEngine> engineFunction) {
            this.selector = selector;
            this.engineFunction = engineFunction;
        }

        public SSLSession getHandshakeSession() {
            return handshakeSession;
        }

        public SSLEngineResult wrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dst) throws SSLException {
            return OK_UNWRAP;
        }

        public SSLEngineResult unwrap(final ByteBuffer src, final ByteBuffer[] dsts, final int offset, final int length) throws SSLException {
            SSLEngine next;
            final int mark = src.position();
            try {
                if (src.remaining() < SNISSLExplorer.RECORD_HEADER_SIZE) {
                    packetBufferSize = SNISSLExplorer.RECORD_HEADER_SIZE;
                    return UNDERFLOW_UNWRAP;
                }
                final int requiredSize = SNISSLExplorer.getRequiredSize(src);
                if (src.remaining() < requiredSize) {
                    packetBufferSize = requiredSize;
                    return UNDERFLOW_UNWRAP;
                }
                List<SNIServerName> names = SNISSLExplorer.explore(src);
                SSLContext sslContext = selector.getContext(names);
                if (sslContext == null) {
                    // no SSL context is available
                    throw UndertowMessages.MESSAGES.noContextForSslConnection();
                }
                next = engineFunction.apply(sslContext);
                if (enabledSuites != null) {
                    next.setEnabledCipherSuites(enabledSuites);
                }
                if (enabledProtocols != null) {
                    next.setEnabledProtocols(enabledProtocols);
                }
                next.setUseClientMode(false);
                final int flagsVal = flags.get();
                if ((flagsVal & FL_WANT_C_AUTH) != 0) {
                    next.setWantClientAuth(true);
                } else if ((flagsVal & FL_NEED_C_AUTH) != 0) {
                    next.setNeedClientAuth(true);
                }
                if ((flagsVal & FL_SESSION_CRE) != 0) {
                    next.setEnableSessionCreation(true);
                }
                next = selectionCallback.apply(next);
                currentRef.set(next);
            } finally {
                src.position(mark);
            }
            return next.unwrap(src, dsts, offset, length);
        }

        public Runnable getDelegatedTask() {
            return null;
        }

        public void closeInbound() throws SSLException {
            currentRef.set(CLOSED_STATE);
        }

        public boolean isInboundDone() {
            return false;
        }

        public void closeOutbound() {
            currentRef.set(CLOSED_STATE);
        }

        public boolean isOutboundDone() {
            return false;
        }

        public String[] getSupportedCipherSuites() {
            if(enabledSuites == null) {
                return new String[0];
            }
            return enabledSuites;
        }

        public String[] getEnabledCipherSuites() {
            return enabledSuites;
        }

        public void setEnabledCipherSuites(final String[] suites) {
            this.enabledSuites = suites;
        }

        public String[] getSupportedProtocols() {
            if(enabledProtocols == null) {
                return new String[0];
            }
            //this kinda sucks, but there is not much else we can do
            return enabledProtocols;
        }

        public String[] getEnabledProtocols() {
            return enabledProtocols;
        }

        public void setEnabledProtocols(final String[] protocols) {
            this.enabledProtocols = protocols;
        }

        public SSLSession getSession() {
            return null;
        }

        public void beginHandshake() throws SSLException {
        }

        public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
            return SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
        }

        public void setUseClientMode(final boolean mode) {
            if (mode) throw new UnsupportedOperationException();
        }

        public boolean getUseClientMode() {
            return false;
        }

        public void setNeedClientAuth(final boolean need) {
            final AtomicInteger flags = this.flags;
            int oldVal, newVal;
            do {
                oldVal = flags.get();
                if (((oldVal & FL_NEED_C_AUTH) != 0) == need) {
                    return;
                }
                newVal = oldVal & FL_SESSION_CRE | FL_NEED_C_AUTH;
            } while (!flags.compareAndSet(oldVal, newVal));
        }

        public boolean getNeedClientAuth() {
            return (flags.get() & FL_NEED_C_AUTH) != 0;
        }

        public void setWantClientAuth(final boolean want) {
            final AtomicInteger flags = this.flags;
            int oldVal, newVal;
            do {
                oldVal = flags.get();
                if (((oldVal & FL_WANT_C_AUTH) != 0) == want) {
                    return;
                }
                newVal = oldVal & FL_SESSION_CRE | FL_WANT_C_AUTH;
            } while (!flags.compareAndSet(oldVal, newVal));
        }

        public boolean getWantClientAuth() {
            return (flags.get() & FL_WANT_C_AUTH) != 0;
        }

        public void setEnableSessionCreation(final boolean flag) {
            final AtomicInteger flags = this.flags;
            int oldVal, newVal;
            do {
                oldVal = flags.get();
                if (((oldVal & FL_SESSION_CRE) != 0) == flag) {
                    return;
                }
                newVal = oldVal ^ FL_SESSION_CRE;
            } while (!flags.compareAndSet(oldVal, newVal));
        }

        public boolean getEnableSessionCreation() {
            return (flags.get() & FL_SESSION_CRE) != 0;
        }
    }

    static final SSLEngine CLOSED_STATE = new SSLEngine() {
        public SSLEngineResult wrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dst) throws SSLException {
            throw new SSLException(new ClosedChannelException());
        }

        public SSLEngineResult unwrap(final ByteBuffer src, final ByteBuffer[] dsts, final int offset, final int length) throws SSLException {
            throw new SSLException(new ClosedChannelException());
        }

        public Runnable getDelegatedTask() {
            return null;
        }

        public void closeInbound() throws SSLException {
        }

        public boolean isInboundDone() {
            return true;
        }

        public void closeOutbound() {

        }

        public boolean isOutboundDone() {
            return true;
        }

        public String[] getSupportedCipherSuites() {
            throw new UnsupportedOperationException();
        }

        public String[] getEnabledCipherSuites() {
            throw new UnsupportedOperationException();
        }

        public void setEnabledCipherSuites(final String[] suites) {
            throw new UnsupportedOperationException();
        }

        public String[] getSupportedProtocols() {
            throw new UnsupportedOperationException();
        }

        public String[] getEnabledProtocols() {
            throw new UnsupportedOperationException();
        }

        public void setEnabledProtocols(final String[] protocols) {
            throw new UnsupportedOperationException();
        }

        public SSLSession getSession() {
            return null;
        }

        public void beginHandshake() throws SSLException {
            throw new SSLException(new ClosedChannelException());
        }

        public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
            return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
        }

        public void setUseClientMode(final boolean mode) {
            throw new UnsupportedOperationException();
        }

        public boolean getUseClientMode() {
            return false;
        }

        public void setNeedClientAuth(final boolean need) {
        }

        public boolean getNeedClientAuth() {
            return false;
        }

        public void setWantClientAuth(final boolean want) {
        }

        public boolean getWantClientAuth() {
            return false;
        }

        public void setEnableSessionCreation(final boolean flag) {
        }

        public boolean getEnableSessionCreation() {
            return false;
        }
    };
}
