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
import org.jboss.logging.Logger;
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
 * This involves swapping out the channel to either use a chunked or fixed length channel.
 * <p/>
 * This should generally be the first handler in any handler chain, as without it persistent connections will not work.
 * <p/>
 * Installing this handler after any other handler that wraps the channel will generally result in broken behaviour,
 * as chunked encoding must be the last transformation applied.
 *
 * @author Stuart Douglas
 */
public class PersistentConnectionHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    private static final Logger log = Logger.getLogger(PersistentConnectionHandler.class);

    private static final boolean traceEnabled;

    static {
        traceEnabled = log.isTraceEnabled();
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {

        boolean persistentConnection = exchange.isHttp11();
        if (exchange.getRequestHeaders().contains(Headers.CONNECTION)) {
            final String connection = exchange.getRequestHeaders().getFirst(Headers.CONNECTION);
            if (!connection.toLowerCase().equals(Headers.KEEP_ALIVE)) {
                persistentConnection = false;
            }
        }
        if (!persistentConnection) {
            //we do not want to wrap the channel
            HttpHandlers.executeHandler(next, exchange, completionHandler);
        } else {
            final TransferCodingChannelWrapper wrapper = new TransferCodingChannelWrapper(completionHandler, exchange);
            exchange.addResponseWrapper(wrapper.getResponseWrapper());
            exchange.addRequestWrapper(wrapper.getRequestWrapper());
            wrapper.handleZeroLengthRequest();
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

        private static final AtomicReferenceFieldUpdater<TransferCodingChannelWrapper, StreamSinkChannel> nextChannelUpdater = AtomicReferenceFieldUpdater.newUpdater(TransferCodingChannelWrapper.class, StreamSinkChannel.class, "nextChannel");

        @SuppressWarnings("unused")
        private volatile StreamSinkChannel nextChannel;
        private volatile boolean zeroContentRequest = false;


        private final ChannelListener<Channel> responseFinishedListener = new ChannelListener<Channel>() {
            @Override
            public void handleEvent(final Channel channel) {
                StreamSinkChannel rc = nextChannelUpdater.get(TransferCodingChannelWrapper.this);
                if (rc == null) {
                    if (!nextChannelUpdater.compareAndSet(TransferCodingChannelWrapper.this, null, exchange.getConnection().getChannel())) {
                        rc = nextChannelUpdater.get(TransferCodingChannelWrapper.this);
                    }
                }
                if (rc instanceof GatedStreamSinkChannel) {
                    ((GatedStreamSinkChannel) rc).openGate(TransferCodingChannelWrapper.this);
                }
            }
        };

        private final ChannelListener<Channel> requestFinishedListener = new ChannelListener<Channel>() {
            @Override
            public void handleEvent(final Channel channel) {
                StreamSinkChannel rc = nextChannelUpdater.get(TransferCodingChannelWrapper.this);
                if (rc == null) {
                    rc = new GatedStreamSinkChannel(exchange.getConnection().getChannel(), TransferCodingChannelWrapper.this, false, false, null);
                    if (!nextChannelUpdater.compareAndSet(TransferCodingChannelWrapper.this, null, rc)) {
                        rc = nextChannelUpdater.get(TransferCodingChannelWrapper.this);
                    }
                }
                final PushBackStreamChannel pushBackStreamChannel = exchange.getConnection().getRequestChannel();
                HttpReadListener readListener = new HttpReadListener(rc, exchange.getConnection());
                pushBackStreamChannel.getReadSetter().set(readListener);
                pushBackStreamChannel.wakeupReads();
            }
        };

        private final ChannelWrapper<StreamSinkChannel> responseWrapper = new ChannelWrapper<StreamSinkChannel>() {
            @Override
            public StreamSinkChannel wrap(final StreamSinkChannel channel, final HttpServerExchange exchange) {
                if (exchange.getResponseHeaders().contains(Headers.CONTENT_LENGTH)) {
                    if (traceEnabled) {
                        log.tracef("Using fixed length channel for response %s", exchange);
                    }
                    long contentLength = Long.parseLong(exchange.getResponseHeaders().get(Headers.CONTENT_LENGTH).getFirst());
                    final FixedLengthStreamSinkChannel wrappedResponse = new FixedLengthStreamSinkChannel(channel, contentLength, false, true, responseFinishedListener);

                    //todo: remove this line when xnio correctly creates delegating setter
                    channel.getWriteSetter().set(ChannelListeners.delegatingChannelListener(wrappedResponse, (ChannelListener.SimpleSetter<FixedLengthStreamSinkChannel>) wrappedResponse.getWriteSetter()));
                    return TransferCodingChannelWrapper.this.wrappedResponse = wrappedResponse;

                } else {
                    if (traceEnabled) {
                        log.tracef("Using chunked channel for response %s", exchange);
                    }
                    exchange.getResponseHeaders().add(Headers.TRANSFER_ENCODING, Headers.CHUNKED);
                    return wrappedResponse = new ChunkedStreamSinkChannel(channel, false, true, responseFinishedListener, exchange.getConnection().getBufferPool());
                }
            }
        };

        private final ChannelWrapper<StreamSourceChannel> requestWrapper = new ChannelWrapper<StreamSourceChannel>() {
            @Override
            public StreamSourceChannel wrap(final StreamSourceChannel channel, final HttpServerExchange exchange) {
                if (exchange.getRequestHeaders().contains(Headers.CONTENT_LENGTH)) {
                    if (traceEnabled) {
                        log.tracef("Using fixed length stream for request %s", exchange);
                    }
                    long contentLength = Long.parseLong(exchange.getRequestHeaders().get(Headers.CONTENT_LENGTH).getFirst());
                    return wrappedRequest = new FixedLengthStreamSourceChannel(channel, contentLength, requestFinishedListener);
                } else if (exchange.getRequestHeaders().contains(Headers.TRANSFER_ENCODING)) {
                    if (traceEnabled) {
                        log.tracef("Using fixed chunked channel for request %s", exchange);
                    }
                    return null; //TODO: chunked channel
                } else {
                    //otherwise we assume no content. This case should be handled by handleZeroContentRequest()
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
            if (traceEnabled) {
                log.tracef("Running persistent connection completion handler for %s", exchange);
            }
            try {
                if (wrappedResponse == null) {
                    if (traceEnabled) {
                        log.tracef("Wrapped response is null, calling getResponseChannel() %s", exchange);
                    }
                    //if the user has not get the response channel we grab it here
                    //we need to force the response to be started, and then we need to call the
                    //finished event once this has finished. If we just called startResponse
                    //here we would not have any way of being notified when it was completed
                    if (!exchange.getResponseHeaders().contains(Headers.CONTENT_LENGTH) &&
                            !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
                    }
                    exchange.getResponseChannel();
                }
                //we just shutdown writes and flush the response
                //when the response is fully flushed the finish listener
                //will handle kicking off the next request
                if (wrappedResponse.isOpen()) {
                    if (traceEnabled) {
                        log.tracef("Response channel is open, shutting down writes %s", exchange);
                    }
                    wrappedResponse.shutdownWrites();
                    if (!wrappedResponse.flush()) {
                        wrappedResponse.getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, ChannelListeners.closingChannelExceptionHandler()));
                        wrappedResponse.wakeupWrites();
                    }
                }

                if (!zeroContentRequest) {
                    StreamSourceChannel req = wrappedRequest;
                    if(req == null) {
                        req = exchange.getUnderlyingRequestChannel();
                    }
                    if (req != null) {
                        if (traceEnabled) {
                            log.tracef("Shutting down wrapped request %s", exchange);
                        }
                        if (req.isOpen()) {
                            if (traceEnabled) {
                                log.tracef("Wrapped request %s is still open, closing", exchange);
                            }
                            req.shutdownReads();
                            long res;
                            do {
                                res = Channels.drain(req, Long.MAX_VALUE);
                            } while (res > 0);
                            if (res == 0) {
                                req.getReadSetter().set(ChannelListeners.<StreamSourceChannel>drainListener(Long.MAX_VALUE, new ChannelListener<SuspendableReadChannel>() {
                                    public void handleEvent(final SuspendableReadChannel channel) {
                                        IoUtils.safeShutdownReads(channel);
                                    }
                                }, ChannelListeners.closingChannelExceptionHandler()));
                                req.resumeReads();
                            }
                        }
                    } else {
                        if (traceEnabled) {
                            log.tracef("Wrapped request is null, invoking finish listener %s", exchange);
                        }
                        requestFinishedListener.handleEvent(null);
                    }
                }
            } catch (IOException e) {
                UndertowLogger.REQUEST_LOGGER.ioExceptionClosingChannel(e);
                delegate.handleComplete();
            }

            //note that we do not delegate by default, as this will close the channel
            //TODO: what other clean up do we have to do here?
        }

        public void handleZeroLengthRequest() {
            if (exchange.getRequestHeaders().contains(Headers.CONTENT_LENGTH)) {
                final String header = exchange.getRequestHeaders().get(Headers.CONTENT_LENGTH).getFirst();
                if (0 == Long.parseLong(header)) {
                    zeroContentRequest = true;
                    requestFinishedListener.handleEvent(null);
                }
            } else if (!exchange.getRequestHeaders().contains(Headers.TRANSFER_ENCODING)) {
                zeroContentRequest = true;
                requestFinishedListener.handleEvent(null);
            }
        }

        public ChannelWrapper<StreamSinkChannel> getResponseWrapper() {
            return responseWrapper;
        }

        public ChannelWrapper<StreamSourceChannel> getRequestWrapper() {
            return requestWrapper;
        }
    }


}
