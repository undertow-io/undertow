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

import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.ChunkedStreamSinkChannel;
import io.undertow.util.ChunkedStreamSourceChannel;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import java.io.IOException;
import java.nio.channels.Channel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.EmptyStreamSourceChannel;
import org.xnio.channels.FixedLengthStreamSinkChannel;
import org.xnio.channels.FixedLengthStreamSourceChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.SuspendableWriteChannel;

/**
 * Handler responsible for dealing with wrapping the response stream and request stream to deal with persistent
 * connections and transfer encodings.
 * <p/>
 * This should generally be the first handler in any handler chain, as without it persistent connections will not work.
 * <p/>
 * Installing this handler after any other handler that wraps the channel will generally result in broken behaviour,
 * as chunked encoding must be the last transformation applied.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @see http://tools.ietf.org/html/rfc2616#section-4.4
 */
public class HttpTransferEncodingHandler implements HttpHandler {

    private static final Logger log = Logger.getLogger("io.undertow.server.handler.transfer-encoding");

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    /**
     * Construct a new instance.
     */
    public HttpTransferEncodingHandler() {
    }

    /**
     * Construct a new instance.
     *
     * @param next the next HTTP handler
     */
    public HttpTransferEncodingHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final HeaderMap requestHeaders = exchange.getRequestHeaders();
        boolean persistentConnection;
        final boolean hasConnectionHeader = requestHeaders.contains(Headers.CONNECTION);
        final boolean hasTransferEncoding = requestHeaders.contains(Headers.TRANSFER_ENCODING);
        final boolean hasContentLength = requestHeaders.contains(Headers.CONTENT_LENGTH);
        if (exchange.isHttp11()) {
            persistentConnection = !(hasConnectionHeader && requestHeaders.getFirst(Headers.CONNECTION).equalsIgnoreCase(Headers.CLOSE));
        } else if (exchange.isHttp10()) {
            persistentConnection = false;
            if (hasConnectionHeader) {
                for (String value : requestHeaders.get(Headers.CONNECTION)) {
                    if (value.equalsIgnoreCase(Headers.KEEP_ALIVE)) {
                        persistentConnection = true;
                        break;
                    }
                }
            }
        } else {
            log.trace("Connection not persistent");
            persistentConnection = false;
        }
        CompletionHandler ourCompletionHandler = new CompletionHandler(exchange, completionHandler);
        String transferEncoding = Headers.IDENTITY;
        if (hasTransferEncoding) {
            transferEncoding = requestHeaders.getLast(Headers.TRANSFER_ENCODING);
        }
        if (!transferEncoding.equalsIgnoreCase(Headers.IDENTITY)) {
            exchange.addRequestWrapper(chunkedStreamSourceChannelWrapper(ourCompletionHandler));
        } else if (hasContentLength) {
            final long contentLength;
            try {
                contentLength = Long.parseLong(requestHeaders.get(Headers.CONTENT_LENGTH).getFirst());
            } catch (NumberFormatException e) {
                log.trace("Invalid request due to unparsable content length");
                // content length is bad; invalid request
                exchange.setResponseCode(400);
                completionHandler.handleComplete();
                return;
            }
            if (contentLength == 0L) {
                log.trace("No content, starting next request");
                // no content - immediately start the next request, returning an empty stream for this one
                exchange.addRequestWrapper(emptyStreamSourceChannelWrapper());
                exchange.terminateRequest();
            } else {
                // fixed-length content - add a wrapper for a fixed-length stream
                if (persistentConnection) {
                    // but only if the connection is persistent; else why bother?
                    exchange.addRequestWrapper(fixedLengthStreamSourceChannelWrapper(ourCompletionHandler, contentLength));
                }
            }
        } else if (hasTransferEncoding) {
            if (transferEncoding.equalsIgnoreCase(Headers.IDENTITY)) {
                log.trace("Connection not persistent (no content length and identity transfer encoding)");
                // make it not persistent
                persistentConnection = false;
            }
        } else if (persistentConnection) {
            // no content - immediately start the next request, returning an empty stream for this one
            exchange.terminateRequest();
            exchange.addRequestWrapper(emptyStreamSourceChannelWrapper());
        }

        //now the response wrapper, to add in the appropriate connection control headers
        exchange.addResponseWrapper(responseWrapper(ourCompletionHandler, persistentConnection));
        HttpHandlers.executeHandler(next, exchange, ourCompletionHandler);
    }

    private static final class CompletionHandler implements HttpCompletionHandler {
        private final HttpServerExchange exchange;
        private final HttpCompletionHandler delegate;
        private volatile StreamSourceChannel requestStream;
        private volatile StreamSinkChannel responseStream;
        @SuppressWarnings("unused")
        private volatile int called;

        private static final AtomicIntegerFieldUpdater<CompletionHandler> calledUpdater = AtomicIntegerFieldUpdater.newUpdater(CompletionHandler.class, "called");

        private CompletionHandler(final HttpServerExchange exchange, final HttpCompletionHandler delegate) {
            this.exchange = exchange;
            this.delegate = delegate;
        }

        public StreamSourceChannel setRequestStream(final StreamSourceChannel requestStream) {
            return this.requestStream = requestStream;
        }

        public StreamSinkChannel setResponseStream(final StreamSinkChannel responseStream) {
            return this.responseStream = responseStream;
        }

        public void handleComplete() {
            if (! calledUpdater.compareAndSet(this, 0, 1)) {
                return;
            }
            // create the channels if they haven't yet been
            exchange.getRequestChannel();
            final ChannelFactory<StreamSinkChannel> factory = exchange.getResponseChannelFactory();
            if (factory != null) factory.create();
            IoUtils.safeClose(requestStream);
            try {
                responseStream.shutdownWrites();
                if (responseStream.flush()) {
                    delegate.handleComplete();
                    return;
                } else {
                    responseStream.getWriteSetter().set(ChannelListeners.flushingChannelListener(new ChannelListener<SuspendableWriteChannel>() {
                        public void handleEvent(final SuspendableWriteChannel channel) {
                            delegate.handleComplete();
                        }
                    }, new ChannelExceptionHandler<Channel>() {
                        public void handleException(final Channel channel, final IOException exception) {
                            delegate.handleComplete();
                        }
                    }));
                    responseStream.resumeWrites();
                    return;
                }
            } catch (Throwable e) {
                // oh well...
                IoUtils.safeClose(responseStream);
                delegate.handleComplete();
            }
        }
    }

    private static ChannelWrapper<StreamSinkChannel> responseWrapper(final CompletionHandler ourCompletionHandler, final boolean requestLooksPersistent) {
        return new ChannelWrapper<StreamSinkChannel>() {
            public StreamSinkChannel wrap(final StreamSinkChannel channel, final HttpServerExchange exchange) {
                final HeaderMap responseHeaders = exchange.getResponseHeaders();
                // test to see if we're still persistent
                boolean stillPersistent = requestLooksPersistent;
                String transferEncoding = Headers.IDENTITY;
                if (responseHeaders.contains(Headers.TRANSFER_ENCODING)) {
                    if (exchange.isHttp11()) {
                        transferEncoding = responseHeaders.getLast(Headers.TRANSFER_ENCODING);
                    } else {
                        // RFC 2616 3.6 last paragraph
                        responseHeaders.remove(Headers.TRANSFER_ENCODING);
                    }
                } else if (exchange.isHttp11() && !responseHeaders.contains(Headers.CONTENT_LENGTH)) {
                    //if we have a HTTP 1.1 request with no transfer encoding and no content length
                    //then we default to chunked, to enable persistent connections to work
                    responseHeaders.put(Headers.TRANSFER_ENCODING, Headers.CHUNKED);
                    transferEncoding = Headers.CHUNKED;
                }
                StreamSinkChannel wrappedChannel;
                final int code = exchange.getResponseCode();
                if (exchange.getRequestMethod().equalsIgnoreCase(Methods.HEAD) || (100 <= code && code <= 199) || code == 204 || code == 304) {
                    final ChannelListener<StreamSinkChannel> finishListener = stillPersistent ? terminateResponseListener(exchange) : null;
                    wrappedChannel = new FixedLengthStreamSinkChannel(channel, 0L, false, !stillPersistent, finishListener, null);
                } else if (!transferEncoding.equalsIgnoreCase("identity")) {
                    final ChannelListener<StreamSinkChannel> finishListener = stillPersistent ? terminateResponseListener(exchange) : null;
                    wrappedChannel = new ChunkedStreamSinkChannel(channel, false, !stillPersistent, finishListener, exchange.getConnection().getBufferPool());
                } else if (responseHeaders.contains(Headers.CONTENT_LENGTH)) {
                    final long contentLength;
                    try {
                        contentLength = Long.parseLong(responseHeaders.get(Headers.CONTENT_LENGTH).getFirst());
                        final ChannelListener<StreamSinkChannel> finishListener = stillPersistent ? terminateResponseListener(exchange) : null;
                        // fixed-length response
                        wrappedChannel = new FixedLengthStreamSinkChannel(channel, contentLength, false, !stillPersistent, finishListener, null);
                    } catch (NumberFormatException e) {
                        // assume that the response is unbounded, but forbid persistence (this will cause subsequent requests to fail when they write their replies)
                        stillPersistent = false;
                        wrappedChannel = new FinishableStreamSinkChannel(channel, terminateResponseListener(exchange));
                    }
                } else {
                    log.trace("Cancelling persistence because response is identity with no content length");
                    // make it not persistent - very unfortunate for the next request handler really...
                    stillPersistent = false;
                    wrappedChannel = new FinishableStreamSinkChannel(channel, terminateResponseListener(exchange));
                }
                if (exchange.isHttp11()) {
                    if (stillPersistent) {
                        // not strictly required but user agents seem to like it
                        responseHeaders.put(Headers.CONNECTION, Headers.KEEP_ALIVE);
                    } else {
                        responseHeaders.put(Headers.CONNECTION, Headers.CLOSE);
                    }
                } else if (exchange.isHttp10()) {
                    if (stillPersistent) {
                        responseHeaders.put(Headers.CONNECTION, Headers.KEEP_ALIVE);
                    } else {
                        responseHeaders.remove(Headers.CONNECTION);
                    }
                }
                return ourCompletionHandler.setResponseStream(wrappedChannel);
            }
        };
    }

    private static ChannelWrapper<StreamSourceChannel> chunkedStreamSourceChannelWrapper(final CompletionHandler ourCompletionHandler) {
        return new ChannelWrapper<StreamSourceChannel>() {
            public StreamSourceChannel wrap(final StreamSourceChannel channel, final HttpServerExchange exchange) {
                return ourCompletionHandler.setRequestStream(new ChunkedStreamSourceChannel((PushBackStreamChannel) channel, chunkedDrainListener(channel, exchange), exchange.getConnection().getBufferPool()));
            }
        };
    }

    private static ChannelWrapper<StreamSourceChannel> fixedLengthStreamSourceChannelWrapper(final CompletionHandler ourCompletionHandler, final long contentLength) {
        return new ChannelWrapper<StreamSourceChannel>() {
            public StreamSourceChannel wrap(final StreamSourceChannel channel, final HttpServerExchange exchange) {
                return ourCompletionHandler.setRequestStream(new FixedLengthStreamSourceChannel(channel, contentLength, false, fixedLengthDrainListener(channel, exchange), null));
            }
        };
    }

    private static ChannelWrapper<StreamSourceChannel> emptyStreamSourceChannelWrapper() {
        return new ChannelWrapper<StreamSourceChannel>() {
            public StreamSourceChannel wrap(final StreamSourceChannel channel, final HttpServerExchange exchange) {
                return new EmptyStreamSourceChannel(channel.getWorker(), channel.getReadThread());
            }
        };
    }

    private static ChannelListener<FixedLengthStreamSourceChannel> fixedLengthDrainListener(final StreamSourceChannel channel, final HttpServerExchange exchange) {
        return new ChannelListener<FixedLengthStreamSourceChannel>() {
            public void handleEvent(final FixedLengthStreamSourceChannel fixedLengthChannel) {
                long remaining = fixedLengthChannel.getRemaining();
                if (remaining > 0L) {
                    // keep it simple - set up an async drain
                    channel.getReadSetter().set(ChannelListeners.drainListener(remaining, terminateRequestListener(exchange), ChannelListeners.closingChannelExceptionHandler()));
                    channel.resumeReads();
                    return;
                }
                exchange.terminateRequest();
            }
        };
    }

    private static ChannelListener<ChunkedStreamSourceChannel> chunkedDrainListener(final StreamSourceChannel channel, final HttpServerExchange exchange) {
        return new ChannelListener<ChunkedStreamSourceChannel>() {
            public void handleEvent(final ChunkedStreamSourceChannel chunkedStreamSourceChannel) {
                channel.getReadSetter().set(ChannelListeners.drainListener(Long.MAX_VALUE, terminateRequestListener(exchange), ChannelListeners.closingChannelExceptionHandler()));
            }
        };
    }

    private static ChannelListener<StreamSinkChannel> terminateResponseListener(final HttpServerExchange exchange) {
        return new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                exchange.terminateResponse();
            }
        };
    }

    private static ChannelListener<StreamSourceChannel> terminateRequestListener(final HttpServerExchange exchange) {
        return new ChannelListener<StreamSourceChannel>() {
            public void handleEvent(final StreamSourceChannel channel) {
                exchange.terminateRequest();
            }
        };
    }

    /**
     * Get the next HTTP handler.
     *
     * @return the next HTTP handler
     */
    public HttpHandler getNext() {
        return next;
    }

    /**
     * Set the next http handler.
     *
     * @param next the next http handler
     */
    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }
}
