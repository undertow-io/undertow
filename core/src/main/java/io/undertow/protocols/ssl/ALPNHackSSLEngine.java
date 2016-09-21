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

package io.undertow.protocols.ssl;

import io.undertow.UndertowLogger;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * SSLEngine wrapper that provides some super hacky ALPN support on JDK8.
 *
 * Even though this is a nasty hack that relies on JDK internals it is still preferable to modifying the boot class path.
 *
 * It is expected to work with all JDK8 versions, however this cannot be guaranteed if the SSL internals are changed
 * in an incompatible way.
 *
 * This class will go away once JDK8 is no longer in use.
 *
 * @author Stuart Douglas
 */
public class ALPNHackSSLEngine extends SSLEngine {

    public static final boolean ENABLED;


    private static final Field HANDSHAKER;
    private static final Field HANDSHAKER_PROTOCOL_VERSION;
    private static final Field HANDSHAKE_HASH;
    private static final Field HANDSHAKE_HASH_VERSION;
    private static final Method HANDSHAKE_HASH_UPDATE;
    private static final Method HANDSHAKE_HASH_PROTOCOL_DETERMINED;
    private static final Field HANDSHAKE_HASH_DATA;
    private static final Field HANDSHAKE_HASH_FIN_MD;

    private static final Class<?> SSL_ENGINE_IMPL_CLASS;

    static {

        boolean enabled = true;
        Field handshaker;
        Field handshakeHash;
        Field handshakeHashVersion;
        Field handshakeHashData;
        Field handshakeHashFinMd;
        Field protocolVersion;
        Method handshakeHashUpdate;
        Method handshakeHashProtocolDetermined;
        Class<?> sslEngineImpleClass;
        try {
            Class<?> protocolVersionClass = Class.forName("sun.security.ssl.ProtocolVersion", true, ClassLoader.getSystemClassLoader());
            sslEngineImpleClass = Class.forName("sun.security.ssl.SSLEngineImpl", true, ClassLoader.getSystemClassLoader());
            handshaker = sslEngineImpleClass.getDeclaredField("handshaker");
            handshaker.setAccessible(true);
            handshakeHash = handshaker.getType().getDeclaredField("handshakeHash");
            handshakeHash.setAccessible(true);
            protocolVersion = handshaker.getType().getDeclaredField("protocolVersion");
            protocolVersion.setAccessible(true);
            handshakeHashVersion = handshakeHash.getType().getDeclaredField("version");
            handshakeHashVersion.setAccessible(true);
            handshakeHashUpdate = handshakeHash.getType().getDeclaredMethod("update", byte[].class, int.class, int.class);
            handshakeHashUpdate.setAccessible(true);
            handshakeHashProtocolDetermined = handshakeHash.getType().getDeclaredMethod("protocolDetermined", protocolVersionClass);
            handshakeHashProtocolDetermined.setAccessible(true);
            handshakeHashData = handshakeHash.getType().getDeclaredField("data");
            handshakeHashData.setAccessible(true);
            handshakeHashFinMd = handshakeHash.getType().getDeclaredField("finMD");
            handshakeHashFinMd.setAccessible(true);

        } catch (Exception e) {
            UndertowLogger.ROOT_LOGGER.debug("JDK8 ALPN Hack failed ", e);
            enabled = false;
            handshaker = null;
            handshakeHash = null;
            handshakeHashVersion = null;
            handshakeHashUpdate = null;
            handshakeHashProtocolDetermined = null;
            handshakeHashData = null;
            handshakeHashFinMd = null;
            protocolVersion = null;
            sslEngineImpleClass = null;
        }
        ENABLED = enabled && !Boolean.getBoolean("io.undertow.disable-jdk8-alpn");
        HANDSHAKER = handshaker;
        HANDSHAKE_HASH = handshakeHash;
        HANDSHAKE_HASH_PROTOCOL_DETERMINED = handshakeHashProtocolDetermined;
        HANDSHAKE_HASH_VERSION = handshakeHashVersion;
        HANDSHAKE_HASH_UPDATE = handshakeHashUpdate;
        HANDSHAKE_HASH_DATA = handshakeHashData;
        HANDSHAKE_HASH_FIN_MD = handshakeHashFinMd;
        HANDSHAKER_PROTOCOL_VERSION = protocolVersion;
        SSL_ENGINE_IMPL_CLASS = sslEngineImpleClass;
    }

    private final SSLEngine delegate;

    //ALPN Hack specific variables
    private boolean unwrapHelloSeen = false;
    private boolean ourHelloSent = false;
    private ALPNHackServerByteArrayOutputStream alpnHackServerByteArrayOutputStream;
    private ALPNHackClientByteArrayOutputStream ALPNHackClientByteArrayOutputStream;
    private List<String> applicationProtocols;
    private String selectedApplicationProtocol;
    private ByteBuffer bufferedWrapData;

    public ALPNHackSSLEngine(SSLEngine delegate) {
        this.delegate = delegate;
    }

    public static boolean isEnabled(SSLEngine engine) {
        if(!ENABLED) {
            return false;
        }
        return SSL_ENGINE_IMPL_CLASS.isAssignableFrom(engine.getClass());
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] byteBuffers, int i, int i1, ByteBuffer byteBuffer) throws SSLException {
        if(bufferedWrapData != null) {
            int prod = bufferedWrapData.remaining();
            byteBuffer.put(bufferedWrapData);
            bufferedWrapData = null;
            return new SSLEngineResult(SSLEngineResult.Status.OK, SSLEngineResult.HandshakeStatus.NEED_WRAP, 0, prod);
        }
        int pos = byteBuffer.position();
        int limit = byteBuffer.limit();
        SSLEngineResult res =  delegate.wrap(byteBuffers, i, i1, byteBuffer);
        if(!ourHelloSent && res.bytesProduced() > 0) {
            if(delegate.getUseClientMode() && applicationProtocols != null && !applicationProtocols.isEmpty()) {
                ourHelloSent = true;
                ALPNHackClientByteArrayOutputStream = replaceClientByteOutput(delegate);
                ByteBuffer newBuf = byteBuffer.duplicate();
                newBuf.flip();
                byte[] data = new byte[newBuf.remaining()];
                newBuf.get(data);
                byte[] newData = ALPNHackClientHelloExplorer.rewriteClientHello(data, applicationProtocols);
                if(newData != null) {
                    byte[] clientHelloMesage = new byte[newData.length - 5];
                    System.arraycopy(newData, 5, clientHelloMesage, 0 , clientHelloMesage.length);
                    ALPNHackClientByteArrayOutputStream.setSentClientHello(clientHelloMesage);
                    byteBuffer.clear();
                    byteBuffer.put(newData);
                }
            } else if (!getUseClientMode()) {
                if(selectedApplicationProtocol != null && alpnHackServerByteArrayOutputStream != null) {
                    byte[] newServerHello = alpnHackServerByteArrayOutputStream.getServerHello(); //this is the new server hello, it will be part of the first TLS plaintext record
                    if (newServerHello != null) {
                        byteBuffer.flip();
                        List<ByteBuffer> records = ALPNHackServerHelloExplorer.extractRecords(byteBuffer);
                        ByteBuffer newData = ALPNHackServerHelloExplorer.createNewOutputRecords(newServerHello, records);
                        byteBuffer.position(pos); //erase the data
                        byteBuffer.limit(limit);
                        if (newData.remaining() > byteBuffer.remaining()) {
                            int old = newData.limit();
                            newData.limit(newData.position() + byteBuffer.remaining());
                            res = new SSLEngineResult(res.getStatus(), res.getHandshakeStatus(), res.bytesConsumed(), newData.remaining());
                            byteBuffer.put(newData);
                            newData.limit(old);
                            bufferedWrapData = newData;
                        } else {
                            res = new SSLEngineResult(res.getStatus(), res.getHandshakeStatus(), res.bytesConsumed(), newData.remaining());
                            byteBuffer.put(newData);
                        }
                    }
                }
            }
        }
        if(res.bytesProduced() > 0) {
            ourHelloSent = true;
        }
        return res;
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer dataToUnwrap, ByteBuffer[] byteBuffers, int i, int i1) throws SSLException {
        if(!unwrapHelloSeen) {
            if(!delegate.getUseClientMode() && applicationProtocols != null) {
                try {
                    List<String> result = ALPNHackClientHelloExplorer.exploreClientHello(dataToUnwrap.duplicate());
                    if(result != null) {
                        for(String protocol : result) {
                            if(applicationProtocols.contains(protocol)) {
                                selectedApplicationProtocol = protocol;
                                break;
                            }
                        }
                    }
                    unwrapHelloSeen = true;
                } catch (BufferUnderflowException e) {
                    return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, SSLEngineResult.HandshakeStatus.NEED_UNWRAP, 0, 0);
                }
            } else if(delegate.getUseClientMode() && ALPNHackClientByteArrayOutputStream != null) {
                if(!dataToUnwrap.hasRemaining()) {
                    return delegate.unwrap(dataToUnwrap, byteBuffers, i, i1);
                }
                try {
                    ByteBuffer dup = dataToUnwrap.duplicate();
                    int type = dup.get();
                    int major = dup.get();
                    int minor = dup.get();
                    if(type == 22 && major == 3 && minor == 3) {
                        //we only care about TLS 1.2
                        //split up the records, there may be multiple when doing a fast session resume
                        List<ByteBuffer> records = ALPNHackServerHelloExplorer.extractRecords(dataToUnwrap.duplicate());

                        ByteBuffer firstRecord = records.get(0); //this will be the handshake record

                        final AtomicReference<String> alpnResult = new AtomicReference<>();
                        ByteBuffer dupFirst = firstRecord.duplicate();
                        dupFirst.position(firstRecord.position() + 5);
                        ByteBuffer firstLessFraming = dupFirst.duplicate();

                        byte[] result = ALPNHackServerHelloExplorer.removeAlpnExtensionsFromServerHello(dupFirst, alpnResult);
                        firstLessFraming.limit(dupFirst.position());
                        unwrapHelloSeen = true;
                        if (result != null) {
                            selectedApplicationProtocol = alpnResult.get();
                            int newFirstRecordLength = result.length + dupFirst.remaining();
                            byte[] newFirstRecord = new byte[newFirstRecordLength];
                            System.arraycopy(result, 0, newFirstRecord, 0, result.length);
                            dupFirst.get(newFirstRecord, result.length, dupFirst.remaining());
                            dataToUnwrap.position(dataToUnwrap.limit());

                            byte[] originalFirstRecord = new byte[firstLessFraming.remaining()];
                            firstLessFraming.get(originalFirstRecord);

                            ByteBuffer newData = ALPNHackServerHelloExplorer.createNewOutputRecords(newFirstRecord, records);
                            dataToUnwrap.clear();
                            dataToUnwrap.put(newData);
                            dataToUnwrap.flip();
                            ALPNHackClientByteArrayOutputStream.setReceivedServerHello(originalFirstRecord);
                        }
                    }
                } catch (BufferUnderflowException e) {
                    return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, SSLEngineResult.HandshakeStatus.NEED_UNWRAP, 0, 0);
                }
            }
        }
        SSLEngineResult res = delegate.unwrap(dataToUnwrap, byteBuffers, i, i1);
        if(!delegate.getUseClientMode() && selectedApplicationProtocol != null && alpnHackServerByteArrayOutputStream == null) {
            alpnHackServerByteArrayOutputStream = replaceServerByteOutput(delegate, selectedApplicationProtocol);
        }
        return res;
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
    public void setEnabledCipherSuites(String[] strings) {
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
    public void setEnabledProtocols(String[] strings) {
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
        delegate.setUseClientMode(b);
    }

    @Override
    public boolean getUseClientMode() {
        return delegate.getUseClientMode();
    }

    @Override
    public void setNeedClientAuth(boolean b) {
        delegate.setNeedClientAuth(b);
    }

    @Override
    public boolean getNeedClientAuth() {
        return delegate.getNeedClientAuth();
    }

    @Override
    public void setWantClientAuth(boolean b) {
        delegate.setWantClientAuth(b);
    }

    @Override
    public boolean getWantClientAuth() {
        return delegate.getWantClientAuth();
    }

    @Override
    public void setEnableSessionCreation(boolean b) {
        delegate.setEnableSessionCreation(b);
    }

    @Override
    public boolean getEnableSessionCreation() {
        return delegate.getEnableSessionCreation();
    }

    /**
     * JDK8 ALPN hack support method.
     *
     * These methods will be removed once JDK8 ALPN support is no longer required
     * @param applicationProtocols
     */
    public void setApplicationProtocols(List<String> applicationProtocols) {
        this.applicationProtocols = applicationProtocols;
    }

    /**
     * JDK8 ALPN hack support method.
     *
     * These methods will be removed once JDK8 ALPN support is no longer required
     */
    public List<String> getApplicationProtocols() {
        return applicationProtocols;
    }

    /**
     * JDK8 ALPN hack support method.
     *
     * These methods will be removed once JDK8 ALPN support is no longer required
     */
    public String getSelectedApplicationProtocol() {
        return selectedApplicationProtocol;
    }


    static ALPNHackServerByteArrayOutputStream replaceServerByteOutput(SSLEngine sslEngine, String selectedAlpnProtocol) {
        try {
            Object handshaker = HANDSHAKER.get(sslEngine);
            Object hash = HANDSHAKE_HASH.get(handshaker);
            ByteArrayOutputStream existing = (ByteArrayOutputStream) HANDSHAKE_HASH_DATA.get(hash);

            ALPNHackServerByteArrayOutputStream out = new ALPNHackServerByteArrayOutputStream(sslEngine, existing.toByteArray(), selectedAlpnProtocol);
            HANDSHAKE_HASH_DATA.set(hash, out);
            return out;
        } catch (Exception e) {
            UndertowLogger.ROOT_LOGGER.debug("Failed to replace hash output stream ", e);
            return null;
        }
    }

    static ALPNHackClientByteArrayOutputStream replaceClientByteOutput(SSLEngine sslEngine) {
        try {
            Object handshaker = HANDSHAKER.get(sslEngine);
            Object hash = HANDSHAKE_HASH.get(handshaker);

            ALPNHackClientByteArrayOutputStream out = new ALPNHackClientByteArrayOutputStream(sslEngine);
            HANDSHAKE_HASH_DATA.set(hash, out);
            return out;
        } catch (Exception e) {
            UndertowLogger.ROOT_LOGGER.debug("Failed to replace hash output stream ", e);
            return null;
        }
    }
    static void regenerateHashes(SSLEngine sslEngineToHack, ByteArrayOutputStream data, byte[]... hashBytes) {
        //hack up the SSL engine internal state
        try {
            Object handshaker = HANDSHAKER.get(sslEngineToHack);
            Object hash = HANDSHAKE_HASH.get(handshaker);
            data.reset();
            Object protocolVersion = HANDSHAKER_PROTOCOL_VERSION.get(handshaker);
            HANDSHAKE_HASH_VERSION.set(hash, -1);
            HANDSHAKE_HASH_PROTOCOL_DETERMINED.invoke(hash, protocolVersion);
            MessageDigest digest = (MessageDigest) HANDSHAKE_HASH_FIN_MD.get(hash);
            digest.reset();
            for (byte[] b : hashBytes) {
                HANDSHAKE_HASH_UPDATE.invoke(hash, b, 0, b.length);
            }
        } catch (Exception e) {
            e.printStackTrace(); //TODO: remove
            throw new RuntimeException(e);
        }
    }
}
