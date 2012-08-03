/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package io.undertow.server;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.undertow.UndertowLogger;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.ChunkedStreamSinkChannel;
import io.undertow.util.GatedStreamSinkChannel;
import io.undertow.util.Headers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.FixedLengthStreamSinkChannel;
import org.xnio.channels.FixedLengthStreamSourceChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.SuspendableReadChannel;

/**
 * Handler responsible for dealing with wrapping the response stream and request stream to deal with persistent
 * connections.
 * <p/>
 * This involves swapping out the channel to either use a chunked or fixed length channel
 *
 * @author Stuart Douglas
 */
public class PersistentConnectionHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {

        boolean persistentConnection = exchange.isHttp11();
        if(exchange.getRequestHeaders().contains(Headers.CONNECTION)) {
            final String connection = exchange.getRequestHeaders().getFirst(Headers.CONNECTION);
            if(!connection.toLowerCase().equals("keep-alive")) {
                persistentConnection = false;
            }
        }
        if (!persistentConnection) {
            //we do not want to wrap the channel if this is not HTTP/1.1
            //or if a Connection: header has been specified
            //TODO: Connection: close and Connection: upgrade mean we do not want the chunked stream
            HttpHandlers.executeHandler(next, exchange, completionHandler);
        } else {
            final TransferCodingChannelWrapper wrapper = new TransferCodingChannelWrapper(completionHandler, exchange);
            exchange.addResponseWrapper(wrapper.getResponseWrapper());
            exchange.addRequestWrapper(wrapper.getRequestWrapper());
            HttpHandlers.executeHandler(next, exchange, wrapper);
        }
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

    private static class TransferCodingChannelWrapper implements HttpCompletionHandler {

        private volatile StreamSinkChannel wrappedResponse = null;
        private volatile StreamSourceChannel wrappedRequest = null;
        private final HttpCompletionHandler delegate;
        private final HttpServerExchange exchange;

        @SuppressWarnings("unused")
        private volatile StreamSinkChannel nextResponseChannel = null;

        private static final AtomicReferenceFieldUpdater<TransferCodingChannelWrapper, StreamSinkChannel> nextResponseUpdater = AtomicReferenceFieldUpdater.newUpdater(TransferCodingChannelWrapper.class, StreamSinkChannel.class, "nextResponseChannel");

        private final ChannelListener<Channel> requestFinishedListener = new ChannelListener<Channel>() {
            @Override
            public void handleEvent(final Channel channel) {
                //the request stream has been closed. We can start the next request. If the response has not been written
                //out yet we need to install a gated stream so the next request does not start writing prematurely
                StreamSinkChannel nextResponse = nextResponseUpdater.get(TransferCodingChannelWrapper.this);
                if (nextResponse == null) {
                    GatedStreamSinkChannel gated = new GatedStreamSinkChannel(exchange.getConnection().getChannel(), TransferCodingChannelWrapper.this, false, false, null);
                    //attempt to set the next response. We don't really care if it fails, because it means that the response
                    //just finished as well, so there is not need for the gated stream anyway
                    if(!nextResponseUpdater.compareAndSet(TransferCodingChannelWrapper.this, null, gated)) {
                        nextResponse = TransferCodingChannelWrapper.this.nextResponseChannel;
                    }
                }
                final PushBackStreamChannel pushBackStreamChannel = exchange.getConnection().getRequestChannel();
                HttpReadListener readListener = new HttpReadListener(nextResponse, exchange.getConnection());
                pushBackStreamChannel.getReadSetter().set(readListener);
                pushBackStreamChannel.wakeupReads();
            }
        };

        private final ChannelListener<Channel> responseFinishedListener = new ChannelListener<Channel>() {
            @Override
            public void handleEvent(final Channel channel) {
                StreamSinkChannel nextResponse = nextResponseUpdater.get(TransferCodingChannelWrapper.this);
                if (nextResponse == null) {
                    //attempt to set the next response. We don't really care if it fails, because it means that the response
                    //just finished as well, so there is not need for the gated stream anyway
                    if(!nextResponseUpdater.compareAndSet(TransferCodingChannelWrapper.this, null, exchange.getConnection().getChannel())) {
                        nextResponse = nextResponseUpdater.get(TransferCodingChannelWrapper.this);
                    }
                }
                if(nextResponse instanceof GatedStreamSinkChannel) {
                    ((GatedStreamSinkChannel)nextResponse).openGate(TransferCodingChannelWrapper.this);
                }
            }
        };

        private final ChannelWrapper<StreamSinkChannel> responseWrapper = new ChannelWrapper<StreamSinkChannel>() {
            @Override
            public StreamSinkChannel wrap(final StreamSinkChannel channel, final HttpServerExchange exchange) {
                if (exchange.getResponseHeaders().contains(Headers.CONTENT_LENGTH)) {
                    long contentLength = Long.parseLong(exchange.getResponseHeaders().get(Headers.CONTENT_LENGTH).getFirst());
                    return wrappedResponse = new FixedLengthStreamSinkChannel(channel, contentLength, false, false, responseFinishedListener);
                } else {
                    exchange.getResponseHeaders().add(Headers.TRANSFER_ENCODING, Headers.CHUNKED);
                    return wrappedResponse = new ChunkedStreamSinkChannel(channel, false, false, responseFinishedListener, exchange.getConnection().getBufferPool());
                }
            }
        };

        private final ChannelWrapper<StreamSourceChannel> requestWrapper = new ChannelWrapper<StreamSourceChannel>() {
            @Override
            public StreamSourceChannel wrap(final StreamSourceChannel channel, final HttpServerExchange exchange) {
                if (exchange.getRequestHeaders().contains(Headers.CONTENT_LENGTH)) {
                    long contentLength = Long.parseLong(exchange.getRequestHeaders().get(Headers.CONTENT_LENGTH).getFirst());
                    return wrappedRequest = new FixedLengthStreamSourceChannel(channel, contentLength, requestFinishedListener);
                } else if(exchange.getRequestHeaders().contains(Headers.TRANSFER_ENCODING)){
                    return null; //TODO: chunked channel
                } else {
                    //otherwise we assume no content
                    return wrappedRequest = new FixedLengthStreamSourceChannel(channel, 0, requestFinishedListener);
                }
            }
        };

        private TransferCodingChannelWrapper(final HttpCompletionHandler delegate, final HttpServerExchange exchange) {
            this.delegate = delegate;
            this.exchange = exchange;
        }

        @Override
        public void handleComplete() {
            try {
                if (wrappedResponse != null) {
                    //we just shutdown writes and flush the response
                    //when the response is fully flushed the finish listener
                    //will handle kicking off the next request
                    if (wrappedResponse.isOpen()) {
                        wrappedResponse.shutdownWrites();
                        if (!wrappedResponse.flush()) {
                            wrappedResponse.getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, ChannelListeners.closingChannelExceptionHandler()));
                        }
                    }
                } else {
                    responseFinishedListener.handleEvent(exchange.getConnection().getChannel());
                }

                if (wrappedRequest != null) {
                    if (wrappedRequest.isOpen()) {
                        wrappedRequest.shutdownReads();
                        long res;
                        do {
                            res = Channels.drain(wrappedRequest, Long.MAX_VALUE);
                        } while (res > 0);
                        if (res == 0) {
                            wrappedRequest.getReadSetter().set(ChannelListeners.<StreamSourceChannel>drainListener(Long.MAX_VALUE, new ChannelListener<SuspendableReadChannel>() {
                                public void handleEvent(final SuspendableReadChannel channel) {
                                    IoUtils.safeShutdownReads(channel);
                                }
                            }, ChannelListeners.closingChannelExceptionHandler()));
                            wrappedRequest.resumeReads();
                        }
                    }
                } else {
                    requestFinishedListener.handleEvent(exchange.getConnection().getChannel());
                }
            } catch (IOException e) {
                UndertowLogger.REQUEST_LOGGER.ioExceptionClosingChannel(e);
                delegate.handleComplete();
            }

            //note that we do not delegate by default, as this will close the channel
            //TODO: what other clean up do we have to do here?
        }

        public ChannelWrapper<StreamSinkChannel> getResponseWrapper() {
            return responseWrapper;
        }

        public ChannelWrapper<StreamSourceChannel> getRequestWrapper() {
            return requestWrapper;
        }
    }


}
