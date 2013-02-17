package io.undertow.ajp;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.WorkerDispatcher;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.EmptyStreamSourceConduit;
import org.xnio.conduits.StreamSinkChannelWrappingConduit;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import static org.xnio.IoUtils.safeClose;

/**
 * @author Stuart Douglas
 */

final class AjpReadListener implements ChannelListener<StreamSourceChannel> {

    private final StreamSinkChannel responseChannel;

    private AjpParseState state = new AjpParseState();
    private HttpServerExchange httpServerExchange;
    private final HttpServerConnection connection;

    private volatile int read = 0;
    private final int maxRequestSize;

    AjpReadListener(final StreamSinkChannel responseChannel, final StreamSourceChannel requestChannel, final HttpServerConnection connection) {
        this.responseChannel = responseChannel;
        this.connection = connection;
        maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);

        httpServerExchange = new HttpServerExchange(connection, requestChannel, this.responseChannel);
        httpServerExchange.addExchangeCompleteListener(new StartNextRequestAction(requestChannel, responseChannel));
    }

    public void handleEvent(final StreamSourceChannel channel) {
        Pooled<ByteBuffer> existing = connection.getExtraBytes();

        final Pooled<ByteBuffer> pooled = existing == null ? connection.getBufferPool().allocate() : existing;
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;

        try {
            int res;
            do {
                if (existing == null) {
                    buffer.clear();
                    try {
                        res = channel.read(buffer);
                    } catch (IOException e) {
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException");
                        }
                        safeClose(channel);
                        return;
                    }
                } else {
                    res = buffer.remaining();
                }
                if (res == 0) {
                    if (!channel.isReadResumed()) {
                        channel.getReadSetter().set(this);
                        channel.resumeReads();
                    }
                    return;
                }
                if (res == -1) {
                    try {
                        channel.shutdownReads();
                        final StreamSinkChannel responseChannel = this.responseChannel;
                        responseChannel.shutdownWrites();
                        // will return false if there's a response queued ahead of this one, so we'll set up a listener then
                        if (!responseChannel.flush()) {
                            responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, null));
                            responseChannel.resumeWrites();
                        }
                    } catch (IOException e) {
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                        }
                        // fuck it, it's all ruined
                        IoUtils.safeClose(channel);
                        return;
                    }
                    return;
                }
                //TODO: we need to handle parse errors
                if (existing != null) {
                    existing = null;
                    connection.setExtraBytes(null);
                } else {
                    buffer.flip();
                }
                int begin = buffer.remaining();
                AjpParser.INSTANCE.parse(buffer, state, httpServerExchange);
                read += (begin - buffer.remaining());
                if (buffer.hasRemaining()) {
                    free = false;
                    connection.setExtraBytes(pooled);
                }
                if (read > maxRequestSize) {
                    UndertowLogger.REQUEST_LOGGER.requestHeaderWasTooLarge(connection.getPeerAddress(), maxRequestSize);
                    IoUtils.safeClose(connection);
                    return;
                }
            } while (!state.isComplete());

            // we remove ourselves as the read listener from the channel;
            // if the http handler doesn't set any then reads will suspend, which is the right thing to do
            channel.getReadSetter().set(null);
            channel.suspendReads();

            final HttpServerExchange httpServerExchange = this.httpServerExchange;
            httpServerExchange.putAttachment(UndertowOptions.ATTACHMENT_KEY, connection.getUndertowOptions());
            AjpConduitWrapper channelWrapper = new AjpConduitWrapper(new AjpResponseConduit(new StreamSinkChannelWrappingConduit(responseChannel), connection.getBufferPool(), httpServerExchange));
            httpServerExchange.addResponseWrapper(channelWrapper);
            httpServerExchange.addRequestWrapper(channelWrapper.getRequestWrapper());

            try {
                httpServerExchange.setRequestScheme(connection.getSslSession() != null ? "https" : "http"); //todo: determine if this is https
                state = null;
                this.httpServerExchange = null;
                connection.getRootHandler().handleRequest(httpServerExchange);

            } catch (Throwable t) {
                //TODO: we should attempt to return a 500 status code in this situation
                UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(t);
                IoUtils.safeClose(channel);
                IoUtils.safeClose(connection);
            }
        } catch (Exception e) {
            UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
            IoUtils.safeClose(connection.getChannel());
        } finally {
            if (free) pooled.free();
        }
    }

    /**
     * Action that starts the next request
     */
    private static class StartNextRequestAction implements ExchangeCompletionListener {

        private StreamSourceChannel requestChannel;
        private StreamSinkChannel responseChannel;


        public StartNextRequestAction(final StreamSourceChannel requestChannel, final StreamSinkChannel responseChannel) {
            this.requestChannel = requestChannel;
            this.responseChannel = responseChannel;
        }

        @Override
        public void exchangeEvent(final HttpServerExchange exchange) {

            final StreamSourceChannel channel = this.requestChannel;
            final AjpReadListener listener = new AjpReadListener(responseChannel, channel, exchange.getConnection());
            if (channel.isReadResumed()) {
                channel.suspendReads();
            }
            WorkerDispatcher.dispatchNextRequest(channel, new DoNextRequestRead(listener, channel));
            responseChannel = null;
            this.requestChannel = null;
        }

        private static class DoNextRequestRead implements Runnable {
            private final AjpReadListener listener;
            private final StreamSourceChannel channel;

            public DoNextRequestRead(AjpReadListener listener, StreamSourceChannel channel) {
                this.listener = listener;
                this.channel = channel;
            }

            @Override
            public void run() {
                listener.handleEvent(channel);
            }
        }
    }

    private class AjpConduitWrapper implements ConduitWrapper<StreamSinkConduit> {

        private final AjpResponseConduit responseConduit;

        private AjpConduitWrapper(AjpResponseConduit responseConduit) {
            this.responseConduit = responseConduit;
        }

        @Override
        public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
            return responseConduit;
        }

        public ConduitWrapper<StreamSourceConduit> getRequestWrapper() {
            return new ConduitWrapper<StreamSourceConduit>() {
                @Override
                public StreamSourceConduit wrap(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exchange) {
                    StreamSourceConduit conduit = factory.create();
                    conduit = new ReadDataStreamSourceConduit(conduit, exchange.getConnection());

                    final HeaderMap requestHeaders = exchange.getRequestHeaders();
                    HttpString transferEncoding = Headers.IDENTITY;
                    Long length;
                    boolean hasTransferEncoding = requestHeaders.contains(Headers.TRANSFER_ENCODING);
                    if (hasTransferEncoding) {
                        transferEncoding = new HttpString(requestHeaders.getLast(Headers.TRANSFER_ENCODING));
                    }

                    if (hasTransferEncoding && !transferEncoding.equals(Headers.IDENTITY)) {
                        length = null; //unkown length
                    } else if (exchange.getRequestHeaders().contains(Headers.CONTENT_LENGTH)) {
                        final long contentLength = Long.parseLong(requestHeaders.get(Headers.CONTENT_LENGTH).getFirst());
                        if (contentLength == 0L) {
                            UndertowLogger.REQUEST_LOGGER.trace("No content, starting next request");
                            // no content - immediately start the next request, returning an empty stream for this one
                            exchange.terminateRequest();
                            return new EmptyStreamSourceConduit(conduit.getReadThread());
                        } else {
                            length = contentLength;
                        }
                    } else {
                        UndertowLogger.REQUEST_LOGGER.trace("No content length or transfer coding, starting next request");
                        // no content - immediately start the next request, returning an empty stream for this one
                        exchange.terminateRequest();
                        return new EmptyStreamSourceConduit(conduit.getReadThread());
                    }
                    return new AjpRequestConduit(conduit, responseConduit, length);
                }
            };
        }
    }


}
