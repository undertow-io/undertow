package io.undertow.server.protocol.proxy2;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.DelegateOpenListener;
import io.undertow.server.OpenListener;
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


/**
 * Implementation of version 2 of the proxy protocol (https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt)
 *
 * @author Ulrich Herberg
 */
class ProxyProtocolV2ReadListener implements ChannelListener<StreamSourceChannel> {

    private static final int MAX_HEADER_LENGTH = 52;

    private static final byte[] SIG = new byte[] {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A};
    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();


    private final StreamConnection streamConnection;
    private final OpenListener openListener;
    private final UndertowXnioSsl ssl;
    private final ByteBufferPool bufferPool;
    private final OptionMap sslOptionMap;

    private InetAddress sourceAddress;
    private InetAddress destAddress;
    private int sourcePort = -1;
    private int destPort = -1;



    ProxyProtocolV2ReadListener(StreamConnection streamConnection, OpenListener openListener, UndertowXnioSsl ssl, ByteBufferPool bufferPool, OptionMap sslOptionMap) {
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
        boolean freeBuffer = true;
        try {
            for (; ; ) {
                int res = streamSourceChannel.read(buffer.getBuffer());
                if (res == -1) {
                    IoUtils.safeClose(streamConnection);
                    return;
                } else if (res == 0) {
                    return;
                } else {
                    buffer.getBuffer().flip();


                    int byteCount = 0;
                    while (byteCount < SIG.length) {
                        byte c = buffer.getBuffer().get();

                        //first we verify that we have the correct protocol
                        if (c != SIG[byteCount]) {
                            throw UndertowMessages.MESSAGES.invalidProxyHeader();
                        }
                        byteCount++;
                    }


                    byte ver_cmd = buffer.getBuffer().get();
                    UndertowLogger.ROOT_LOGGER.errorf("  ver_cmd: 0x" + byteToHex(ver_cmd));

                    byte fam = buffer.getBuffer().get();
                    UndertowLogger.ROOT_LOGGER.errorf("  fam: 0x" + byteToHex(fam));

                    int len = (buffer.getBuffer().getShort() & 0xffff);
                    UndertowLogger.ROOT_LOGGER.errorf("  len: " + len);


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

                                    UndertowLogger.ROOT_LOGGER.errorf("sourceAddress: %s, destAddress: %s, sourcePort: %d, destPort: %d", sourceAddress.toString(), destAddress.toString(), sourcePort, destPort);

                                    if (len > 12) {
                                        int skipAhead = len - 12;
                                        UndertowLogger.ROOT_LOGGER.errorf("Skipping over extra %d bytes", skipAhead);
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

                                    UndertowLogger.ROOT_LOGGER.errorf("sourceAddress: %s, destAddress: %s, sourcePort: %d, destPort: %d", sourceAddress.toString(), destAddress.toString(), sourcePort, destPort);

                                    if (len > 36) {
                                        int skipAhead = len - 36;
                                        UndertowLogger.ROOT_LOGGER.errorf("Skipping over extra %d bytes", skipAhead);
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
                            UndertowLogger.ROOT_LOGGER.errorf("LOCAL command");

                            if (buffer.getBuffer().hasRemaining()) {
                                freeBuffer = false;
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
                        UndertowLogger.ROOT_LOGGER.errorf("still remaining buffer");
                        freeBuffer = false;
                        proxyAccept(s, d, buffer);
                    } else {
                        proxyAccept(s, d, null);
                    }
                    return;
                }
            }

        } catch (IOException e) {
            UndertowLogger.ROOT_LOGGER.errorf("IOException %s", e.getMessage());

            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            IoUtils.safeClose(streamConnection);
        } catch (Exception e) {
            UndertowLogger.ROOT_LOGGER.errorf("Exception %s", e.getMessage());

            UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(e));
            IoUtils.safeClose(streamConnection);
        } finally {
            if (freeBuffer) {
                buffer.close();
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
//            UndertowLogger.ROOT_LOGGER.errorf("parsing additional data");

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


    private static String byteToHex(byte abyte) {
        char[] hexChars = new char[2];
        int v = abyte & 0xFF;
        hexChars[0] = hexArray[v >>> 4];
        hexChars[1] = hexArray[v & 0x0F];

        return new String(hexChars);
    }
}
