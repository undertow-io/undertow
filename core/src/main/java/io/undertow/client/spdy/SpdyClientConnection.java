package io.undertow.client.spdy;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.spdy.SpdyChannel;
import io.undertow.spdy.SpdyPingStreamSourceChannel;
import io.undertow.spdy.SpdyStreamSourceChannel;
import io.undertow.spdy.SpdySynReplyStreamSourceChannel;
import io.undertow.spdy.SpdySynStreamStreamSinkChannel;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Headers.TRANSFER_ENCODING;

/**
 * @author Stuart Douglas
 */
public class SpdyClientConnection implements ClientConnection {


    static final HttpString METHOD = new HttpString(":method");
    static final HttpString PATH = new HttpString(":path");
    static final HttpString SCHEME = new HttpString(":scheme");
    static final HttpString VERSION = new HttpString(":version");
    static final HttpString HOST = new HttpString(":host");
    static final HttpString STATUS = new HttpString(":status");

    private final SpdyChannel spdyChannel;
    private final ChannelListener.SimpleSetter<ClientConnection> closeSetter = new ChannelListener.SimpleSetter<ClientConnection>();

    private final Map<Integer, SpdyClientExchange> currentExchanges = new ConcurrentHashMap<Integer, SpdyClientExchange>();

    public SpdyClientConnection(SpdyChannel spdyChannel) {
        this.spdyChannel = spdyChannel;
        spdyChannel.getReceiveSetter().set(new SpdyRecieveListener());
        spdyChannel.resumeReceives();
    }

    @Override
    public void sendRequest(ClientRequest request, ClientCallback<ClientExchange> clientCallback) {
        request.getRequestHeaders().add(PATH, request.getPath());
        request.getRequestHeaders().add(SCHEME, "https");
        request.getRequestHeaders().add(VERSION, request.getProtocol().toString());
        request.getRequestHeaders().add(METHOD, request.getMethod().toString());
        request.getRequestHeaders().add(HOST, request.getRequestHeaders().getFirst(Headers.HOST));

        SpdySynStreamStreamSinkChannel sinkChannel = spdyChannel.createStream(request.getRequestHeaders());
        SpdyClientExchange exchange = new SpdyClientExchange(this, sinkChannel, request);
        currentExchanges.put(sinkChannel.getStreamId(), exchange);


        boolean hasContent = true;

        String fixedLengthString = request.getRequestHeaders().getFirst(CONTENT_LENGTH);
        String transferEncodingString = request.getRequestHeaders().getLast(TRANSFER_ENCODING);
        if (fixedLengthString != null) {
            try {
                long length = Long.parseLong(fixedLengthString);
                hasContent = length != 0;
            } catch (NumberFormatException e) {
                handleError(new IOException(e));
                return;
            }
        } else if (transferEncodingString == null) {
            hasContent = false;
        }
        if(clientCallback != null) {
            clientCallback.completed(exchange);
        }
        if (!hasContent) {
            //if there is no content we flush the response channel.
            //otherwise it is up to the user
            try {
                sinkChannel.shutdownWrites();
                if (!sinkChannel.flush()) {
                    sinkChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, new ChannelExceptionHandler<StreamSinkChannel>() {
                        @Override
                        public void handleException(StreamSinkChannel channel, IOException exception) {
                            handleError(exception);
                        }
                    }));
                }
            } catch (IOException e) {
                handleError(e);
            }
        } else if (!sinkChannel.isWriteResumed()) {
            try {
                //TODO: this needs some more thought
                if (!sinkChannel.flush()) {
                    sinkChannel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        @Override
                        public void handleEvent(StreamSinkChannel channel) {
                            try {
                                if (channel.flush()) {
                                    channel.suspendWrites();
                                }
                            } catch (IOException e) {
                                handleError(e);
                            }
                        }
                    });
                    sinkChannel.resumeWrites();
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void handleError(IOException e) {

        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
        IoUtils.safeClose(SpdyClientConnection.this);
        for (Map.Entry<Integer, SpdyClientExchange> entry : currentExchanges.entrySet()) {
            try {
                entry.getValue().failed(e);
            } catch (Exception ex) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(ex));
            }
        }
    }

    @Override
    public StreamConnection performUpgrade() throws IOException {
        throw UndertowMessages.MESSAGES.upgradeNotSupported();
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        return spdyChannel.getBufferPool();
    }

    @Override
    public SocketAddress getPeerAddress() {
        return spdyChannel.getPeerAddress();
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        return spdyChannel.getPeerAddress(type);
    }

    @Override
    public ChannelListener.Setter<? extends ClientConnection> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return spdyChannel.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return spdyChannel.getLocalAddress(type);
    }

    @Override
    public XnioWorker getWorker() {
        return spdyChannel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return spdyChannel.getIoThread();
    }

    @Override
    public boolean isOpen() {
        return spdyChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        spdyChannel.close();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return false;
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return null;
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return null;
    }

    @Override
    public boolean isUpgraded() {
        return false;
    }

    private class SpdyRecieveListener implements ChannelListener<SpdyChannel> {

        @Override
        public void handleEvent(SpdyChannel channel) {
            try {
                SpdyStreamSourceChannel result = channel.receive();
                if (result instanceof SpdySynReplyStreamSourceChannel) {
                    SpdyClientExchange request = currentExchanges.remove(((SpdySynReplyStreamSourceChannel) result).getStreamId());
                    if (request == null) {
                        //server side initiated stream, we can't deal with that at the moment
                        //just fail
                        //TODO: either handle this properly or at the very least send RST_STREAM
                        IoUtils.safeClose(SpdyClientConnection.this);
                        return;
                    }
                    request.responseReady((SpdySynReplyStreamSourceChannel) result);

                } else if (result instanceof SpdyPingStreamSourceChannel) {
                    handlePing((SpdyPingStreamSourceChannel) result);
                }

            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(SpdyClientConnection.this);
                for (Map.Entry<Integer, SpdyClientExchange> entry : currentExchanges.entrySet()) {
                    try {
                        entry.getValue().failed(e);
                    } catch (Exception ex) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(ex));
                    }
                }
            }

        }

        private void handlePing(SpdyPingStreamSourceChannel frame) {
            int id = frame.getId();
            if (id % 2 == 0) {
                //server side ping, return it
                frame.getSpdyChannel().sendPing(id);
            }
        }

    }
}
