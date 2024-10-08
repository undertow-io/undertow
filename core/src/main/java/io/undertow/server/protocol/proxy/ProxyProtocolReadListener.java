package io.undertow.server.protocol.proxy;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.DelegateOpenListener;
import io.undertow.server.OpenListener;
import io.undertow.util.NetworkUtils;
import io.undertow.util.PooledAdaptor;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.PushBackStreamSourceConduit;
import org.xnio.ssl.SslConnection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of version 1 and 2 of the proxy protocol (https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt)
 * <p>
 * Even though it is not required by the spec this implementation provides a stateful parser, that can handle
 * fragmentation of
 *
 * @author Stuart Douglas
 * @author Ulrich Herberg
 */
class ProxyProtocolReadListener implements ChannelListener<StreamSourceChannel> {

    private static final int MAX_HEADER_LENGTH = 107;

    private static final byte[] NAME = "PROXY ".getBytes(StandardCharsets.US_ASCII);
    private static final String UNKNOWN = "UNKNOWN";
    private static final String TCP4 = "TCP4";
    private static final String TCP6 = "TCP6";

    private static final byte[] SIG = new byte[] {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A};

    private final StreamConnection streamConnection;
    private final OpenListener openListener;
    private final UndertowXnioSsl ssl;
    private final ByteBufferPool bufferPool;
    private final OptionMap sslOptionMap;

    private int byteCount;
    private String protocol;
    private InetAddress sourceAddress;
    private InetAddress destAddress;
    private int sourcePort = -1;
    private int destPort = -1;
    private StringBuilder stringBuilder = new StringBuilder();
    private boolean carriageReturnSeen = false;
    private boolean parsingUnknown = false;


    ProxyProtocolReadListener(StreamConnection streamConnection, OpenListener openListener, UndertowXnioSsl ssl, ByteBufferPool bufferPool, OptionMap sslOptionMap) {
        this.streamConnection = streamConnection;
        this.openListener = openListener;
        this.ssl = ssl;
        this.bufferPool = bufferPool;
        this.sslOptionMap = sslOptionMap;
        if (bufferPool.getBufferSize() < MAX_HEADER_LENGTH) {
            throw UndertowMessages.MESSAGES.bufferPoolTooSmall(MAX_HEADER_LENGTH);
        }
    }

    @Override
    public void handleEvent(StreamSourceChannel streamSourceChannel) {
        PooledByteBuffer buffer = bufferPool.allocate();
        AtomicBoolean freeBuffer = new AtomicBoolean(true);
        try {
            int res = streamSourceChannel.read(buffer.getBuffer());
            if (res == -1) {
                IoUtils.safeClose(streamConnection);
                return;
            } else if (res == 0) {
                return;
            } else {
                buffer.getBuffer().flip();

                if (buffer.getBuffer().hasRemaining()) {
                    byte firstByte = buffer.getBuffer().get(); // get first byte to determine whether Proxy Protocol V1 or V2 is used
                    byteCount++;
                    if (firstByte == SIG[0]) {  // Could be Proxy Protocol V2
                        parseProxyProtocolV2(buffer, freeBuffer);
                    } else if ((char) firstByte == NAME[0]){ // Could be Proxy Protocol V1
                        parseProxyProtocolV1(buffer, freeBuffer);
                    } else {
                        throw UndertowMessages.MESSAGES.invalidProxyHeader();
                    }
                }
                return;
            }

        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            IoUtils.safeClose(streamConnection);
        } catch (Exception e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(e));
            IoUtils.safeClose(streamConnection);
        } finally {
            if (freeBuffer.get()) {
                buffer.close();
            }
        }
    }



    private void parseProxyProtocolV2(PooledByteBuffer buffer, AtomicBoolean freeBuffer) throws Exception {
        while (byteCount < SIG.length) {
            byte c = buffer.getBuffer().get();

            //first we verify that we have the correct protocol
            if (c != SIG[byteCount]) {
                throw UndertowMessages.MESSAGES.invalidProxyHeader();
            }
            byteCount++;
        }

        byte ver_cmd = buffer.getBuffer().get();
        byte fam = buffer.getBuffer().get();
        int len = (buffer.getBuffer().getShort() & 0xffff);

        if ((ver_cmd & 0xF0) != 0x20) {  // expect version 2
            throw UndertowMessages.MESSAGES.invalidProxyHeader();
        }

        switch (ver_cmd & 0x0F) {
            case 0x01:  // PROXY command
                switch (fam) {
                    case 0x11: { // TCP over IPv4
                        if (len < 12) {
                            throw UndertowMessages.MESSAGES.invalidProxyHeader();
                        }

                        byte[] sourceAddressBytes = new byte[4];
                        buffer.getBuffer().get(sourceAddressBytes);
                        sourceAddress = InetAddress.getByAddress(sourceAddressBytes);

                        byte[] dstAddressBytes = new byte[4];
                        buffer.getBuffer().get(dstAddressBytes);
                        destAddress = InetAddress.getByAddress(dstAddressBytes);

                        sourcePort = buffer.getBuffer().getShort() & 0xffff;
                        destPort = buffer.getBuffer().getShort() & 0xffff;

                        if (len > 12) {
                            int skipAhead = len - 12;
                            int currentPosition = buffer.getBuffer().position();
                            buffer.getBuffer().position(currentPosition + skipAhead);
                        }

                        break;
                    }

                    case 0x21: { // TCP over IPv6
                        if (len < 36) {
                            throw UndertowMessages.MESSAGES.invalidProxyHeader();
                        }

                        byte[] sourceAddressBytes = new byte[16];
                        buffer.getBuffer().get(sourceAddressBytes);
                        sourceAddress = InetAddress.getByAddress(sourceAddressBytes);

                        byte[] dstAddressBytes = new byte[16];
                        buffer.getBuffer().get(dstAddressBytes);
                        destAddress = InetAddress.getByAddress(dstAddressBytes);

                        sourcePort = buffer.getBuffer().getShort() & 0xffff;
                        destPort = buffer.getBuffer().getShort() & 0xffff;

                        if (len > 36) {
                            int skipAhead = len - 36;
                            int currentPosition = buffer.getBuffer().position();
                            buffer.getBuffer().position(currentPosition + skipAhead);
                        }

                        break;
                    }

                    default: // AF_UNIX sockets not supported
                        throw UndertowMessages.MESSAGES.invalidProxyHeader();

                }
                break;
            case 0x00: // LOCAL command
                if (len > 0) {
                    int skipAhead = len;
                    int currentPosition = buffer.getBuffer().position();
                    buffer.getBuffer().position(currentPosition + skipAhead);
                }

                if (buffer.getBuffer().hasRemaining()) {
                    freeBuffer.set(false);
                    proxyAccept(null, null, buffer);
                } else {
                    proxyAccept(null, null, null);
                }
                return;
            default:
                throw UndertowMessages.MESSAGES.invalidProxyHeader();
        }


        SocketAddress s = new InetSocketAddress(sourceAddress, sourcePort);
        SocketAddress d = new InetSocketAddress(destAddress, destPort);
        if (buffer.getBuffer().hasRemaining()) {
            freeBuffer.set(false);
            proxyAccept(s, d, buffer);
        } else {
            proxyAccept(s, d, null);
        }
        return;
    }

    private void parseProxyProtocolV1(PooledByteBuffer buffer, AtomicBoolean freeBuffer) throws Exception {
        while (buffer.getBuffer().hasRemaining()) {
            char c = (char) buffer.getBuffer().get();
            if (byteCount < NAME.length) {
                //first we verify that we have the correct protocol
                if (c != NAME[byteCount]) {
                    throw UndertowMessages.MESSAGES.invalidProxyHeader();
                }
            } else {
                if (parsingUnknown) {
                    //we are parsing the UNKNOWN protocol
                    //we just ignore everything till \r\n
                    if (c == '\r') {
                        carriageReturnSeen = true;
                    } else if (c == '\n') {
                        if (!carriageReturnSeen) {
                            throw UndertowMessages.MESSAGES.invalidProxyHeader();
                        }
                        //we are done
                        if (buffer.getBuffer().hasRemaining()) {
                            freeBuffer.set(false);
                            proxyAccept(null, null, buffer);
                        } else {
                            proxyAccept(null, null, null);
                        }
                        return;
                    } else if (carriageReturnSeen) {
                        throw UndertowMessages.MESSAGES.invalidProxyHeader();
                    }
                } else if (carriageReturnSeen) {
                    if (c == '\n') {
                        //we are done
                        SocketAddress s = new InetSocketAddress(sourceAddress, sourcePort);
                        SocketAddress d = new InetSocketAddress(destAddress, destPort);
                        if (buffer.getBuffer().hasRemaining()) {
                            freeBuffer.set(false);
                            proxyAccept(s, d, buffer);
                        } else {
                            proxyAccept(s, d, null);
                        }
                        return;
                    } else {
                        throw UndertowMessages.MESSAGES.invalidProxyHeader();
                    }
                } else switch (c) {
                    case ' ':
                        //we have a space
                        if (sourcePort != -1 || stringBuilder.length() == 0) {
                            //header was invalid, either we are expecting a \r or a \n, or the previous character was a space
                            throw UndertowMessages.MESSAGES.invalidProxyHeader();
                        } else if (protocol == null) {
                            protocol = stringBuilder.toString();
                            stringBuilder.setLength(0);
                            if (protocol.equals(UNKNOWN)) {
                                parsingUnknown = true;
                            } else if (!protocol.equals(TCP4) && !protocol.equals(TCP6)) {
                                throw UndertowMessages.MESSAGES.invalidProxyHeader();
                            }
                        } else if (sourceAddress == null) {
                            sourceAddress = parseAddress(stringBuilder.toString(), protocol);
                            stringBuilder.setLength(0);
                        } else if (destAddress == null) {
                            destAddress = parseAddress(stringBuilder.toString(), protocol);
                            stringBuilder.setLength(0);
                        } else {
                            sourcePort = Integer.parseInt(stringBuilder.toString());
                            stringBuilder.setLength(0);
                        }
                        break;
                    case '\r':
                        if (destPort == -1 && sourcePort != -1 && !carriageReturnSeen && stringBuilder.length() > 0) {
                            destPort = Integer.parseInt(stringBuilder.toString());
                            stringBuilder.setLength(0);
                            carriageReturnSeen = true;
                        } else if (protocol == null) {
                            if (UNKNOWN.equals(stringBuilder.toString())) {
                                parsingUnknown = true;
                                carriageReturnSeen = true;
                            }
                        } else {
                            throw UndertowMessages.MESSAGES.invalidProxyHeader();
                        }
                        break;
                    case '\n':
                        throw UndertowMessages.MESSAGES.invalidProxyHeader();
                    default:
                        stringBuilder.append(c);
                }

            }

            byteCount++;
            if (byteCount == MAX_HEADER_LENGTH) {
                throw UndertowMessages.MESSAGES.headerSizeToLarge();
            }

        }
    }


    private void proxyAccept(SocketAddress source, SocketAddress dest, PooledByteBuffer additionalData) {
        StreamConnection streamConnection = this.streamConnection;
        if (source != null) {
            streamConnection = new AddressWrappedConnection(streamConnection, source, dest);
        }
        if (ssl != null) {

            //we need to apply the additional data before the SSL wrapping
            if (additionalData != null) {
                PushBackStreamSourceConduit conduit = new PushBackStreamSourceConduit(streamConnection.getSourceChannel().getConduit());
                conduit.pushBack(new PooledAdaptor(additionalData));
                streamConnection.getSourceChannel().setConduit(conduit);
            }
            SslConnection sslConnection = ssl.wrapExistingConnection(streamConnection, sslOptionMap == null ? OptionMap.EMPTY : sslOptionMap, false);
            streamConnection = sslConnection;

            callOpenListener(streamConnection, null);
        } else {
            callOpenListener(streamConnection, additionalData);
        }
    }


    private void callOpenListener(StreamConnection streamConnection, final PooledByteBuffer buffer) {
        if (openListener instanceof DelegateOpenListener) {
            ((DelegateOpenListener) openListener).handleEvent(streamConnection, buffer);
        } else {
            if (buffer != null) {
                PushBackStreamSourceConduit conduit = new PushBackStreamSourceConduit(streamConnection.getSourceChannel().getConduit());
                conduit.pushBack(new PooledAdaptor(buffer));
                streamConnection.getSourceChannel().setConduit(conduit);
            }
            openListener.handleEvent(streamConnection);
        }
    }

    static InetAddress parseAddress(String addressString, String protocol) throws IOException {
        if (protocol.equals(TCP4)) {
            return NetworkUtils.parseIpv4Address(addressString);
        } else {
            return NetworkUtils.parseIpv6Address(addressString);
        }
    }

    private static final class AddressWrappedConnection extends StreamConnection {

        private final StreamConnection delegate;
        private final SocketAddress source;
        private final SocketAddress dest;

        AddressWrappedConnection(StreamConnection delegate, SocketAddress source, SocketAddress dest) {
            super(delegate.getIoThread());
            this.delegate = delegate;
            this.source = source;
            this.dest = dest;
            setSinkConduit(delegate.getSinkChannel().getConduit());
            setSourceConduit(delegate.getSourceChannel().getConduit());
        }

        @Override
        protected void notifyWriteClosed() {
            IoUtils.safeClose(delegate.getSinkChannel());
        }

        @Override
        protected void notifyReadClosed() {
            IoUtils.safeClose(delegate.getSourceChannel());
        }

        @Override
        public SocketAddress getPeerAddress() {
            return source;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return dest;
        }
    }
}
