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

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.conduits.BrokenStreamSourceConduit;
import io.undertow.conduits.ChunkedStreamSinkConduit;
import io.undertow.conduits.ChunkedStreamSourceConduit;
import io.undertow.conduits.ConduitListener;
import io.undertow.conduits.FinishableStreamSinkConduit;
import io.undertow.conduits.FixedLengthStreamSinkConduit;
import io.undertow.conduits.FixedLengthStreamSourceConduit;
import io.undertow.conduits.PipelingBufferingStreamSinkConduit;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.jboss.logging.Logger;
import org.xnio.XnioExecutor;
import org.xnio.conduits.EmptyStreamSourceConduit;
import org.xnio.conduits.StreamSinkChannelWrappingConduit;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * Class that is  responsible for HTTP transfer encooding, this could be part of the {@link HttpReadListener},
 * but is seperated out for clarity
 *
 * @author Stuart Douglas
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @see http://tools.ietf.org/html/rfc2616#section-4.4
 */
public class HttpTransferEncoding {

    private static final Logger log = Logger.getLogger("io.undertow.server.handler.transfer-encoding");

    /**
     * Construct a new instance.
     */
    private HttpTransferEncoding() {
    }

    public static void handleRequest(final HttpServerExchange exchange, final HttpHandler next) {
        final HeaderMap requestHeaders = exchange.getRequestHeaders();
        final String connectionHeader = requestHeaders.getFirst(Headers.CONNECTION);
        final String transferEncodingHeader = requestHeaders.getLast(Headers.TRANSFER_ENCODING);
        final String contentLengthHeader = requestHeaders.getFirst(Headers.CONTENT_LENGTH);

        final HttpServerConnection connection = exchange.getConnection();
        //if we are already using the pipelineing buffer add it to the exchange
        PipelingBufferingStreamSinkConduit pipeliningBuffer = connection.getAttachment(PipelingBufferingStreamSinkConduit.ATTACHMENT_KEY);
        if(pipeliningBuffer != null) {
            exchange.addResponseWrapper(pipeliningBuffer.getChannelWrapper());
        }

        exchange.addRequestWrapper(ReadDataStreamSourceConduit.WRAPPER);

        boolean persistentConnection = persistentConnection(exchange, connectionHeader);

        if(exchange.getRequestMethod().equals(Methods.GET)) {
            if(persistentConnection
                    && connection.getExtraBytes() != null
                    && pipeliningBuffer == null
                    && connection.getUndertowOptions().get(UndertowOptions.BUFFER_PIPELINED_DATA, false)) {
                pipeliningBuffer = new PipelingBufferingStreamSinkConduit(new StreamSinkChannelWrappingConduit(connection.getChannel().getSinkChannel()), connection.getBufferPool());
                connection.putAttachment(PipelingBufferingStreamSinkConduit.ATTACHMENT_KEY, pipeliningBuffer);
                exchange.addResponseWrapper(pipeliningBuffer.getChannelWrapper());
            }
            // no content - immediately start the next request, returning an empty stream for this one
            exchange.terminateRequest();
            exchange.addRequestWrapper(EMPTY_STREAM_SOURCE_CONDUIT_WRAPPER);
        } else {
            persistentConnection = handleRequestEncoding(exchange, transferEncodingHeader, contentLengthHeader, connection, pipeliningBuffer, persistentConnection);
        }

        exchange.setPersistent(persistentConnection);

        exchange.addResponseWrapper(HttpResponseConduit.WRAPPER);
        //now the response wrapper, to add in the appropriate connection control headers
        exchange.addResponseWrapper(responseWrapper(persistentConnection));

        HttpHandlers.executeRootHandler(next, exchange, Thread.currentThread() instanceof XnioExecutor);
    }

    private static boolean handleRequestEncoding(HttpServerExchange exchange, String transferEncodingHeader, String contentLengthHeader, HttpServerConnection connection, PipelingBufferingStreamSinkConduit pipeliningBuffer, boolean persistentConnection) {
        HttpString transferEncoding = Headers.IDENTITY;
        if (transferEncodingHeader != null) {
            transferEncoding = new HttpString(transferEncodingHeader);
        }
        if (transferEncodingHeader != null && !transferEncoding.equals(Headers.IDENTITY)) {
            exchange.addRequestWrapper(CHUNKED_STREAM_SOURCE_CONDUIT_WRAPPER);
        } else if (contentLengthHeader != null) {
            final long contentLength;
                contentLength = Long.parseLong(contentLengthHeader);
            if (contentLength == 0L) {
                log.trace("No content, starting next request");
                // no content - immediately start the next request, returning an empty stream for this one
                exchange.addRequestWrapper(EMPTY_STREAM_SOURCE_CONDUIT_WRAPPER);
                exchange.terminateRequest();
            } else {
                // fixed-length content - add a wrapper for a fixed-length stream
                exchange.addRequestWrapper(fixedLengthStreamSourceConduitWrapper(contentLength));
            }
        } else if (transferEncodingHeader != null) {
            if (transferEncoding.equals(Headers.IDENTITY)) {
                log.trace("Connection not persistent (no content length and identity transfer encoding)");
                // make it not persistent
                persistentConnection = false;
            }
        } else if (persistentConnection) {
            //we have no content and a persistent request. This may mean we need to use the pipelining buffer to improve
            //performance
            if (connection.getExtraBytes() != null
                    && pipeliningBuffer == null
                    && connection.getUndertowOptions().get(UndertowOptions.BUFFER_PIPELINED_DATA, false)) {
                pipeliningBuffer = new PipelingBufferingStreamSinkConduit(new StreamSinkChannelWrappingConduit(connection.getChannel().getSinkChannel()), connection.getBufferPool());
                connection.putAttachment(PipelingBufferingStreamSinkConduit.ATTACHMENT_KEY, pipeliningBuffer);
                exchange.addResponseWrapper(pipeliningBuffer.getChannelWrapper());
            }

            // no content - immediately start the next request, returning an empty stream for this one
            exchange.terminateRequest();
            exchange.addRequestWrapper(EMPTY_STREAM_SOURCE_CONDUIT_WRAPPER);
        } else if (exchange.isHttp11()) {
            //this is a http 1.1 non-persistent connection
            //we still know there is no content
            exchange.terminateRequest();
            exchange.addRequestWrapper(EMPTY_STREAM_SOURCE_CONDUIT_WRAPPER);
        }
        return persistentConnection;
    }

    private static boolean persistentConnection(HttpServerExchange exchange, String connectionHeader) {
        if (exchange.isHttp11()) {
            return !(connectionHeader != null && new HttpString(connectionHeader).equals(Headers.CLOSE));
        } else if (exchange.isHttp10()) {
            if (connectionHeader != null) {
                if (Headers.KEEP_ALIVE.equals(new HttpString(connectionHeader))) {
                    return true;
                }
            }
        }
        log.trace("Connection not persistent");
        return false;
    }

    private static ConduitWrapper<StreamSinkConduit> responseWrapper(final boolean requestLooksPersistent) {
        return new ConduitWrapper<StreamSinkConduit>() {
            public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
                final StreamSinkConduit channel = factory.create();
                final HeaderMap responseHeaders = exchange.getResponseHeaders();
                // test to see if we're still persistent
                boolean stillPersistent = requestLooksPersistent;
                HttpString transferEncoding = Headers.IDENTITY;
                final String transferEncodingHeader = responseHeaders.getLast(Headers.TRANSFER_ENCODING);
                final String contentLengthHeader = responseHeaders.getFirst(Headers.CONTENT_LENGTH);
                if (transferEncodingHeader != null) {
                    if (exchange.isHttp11()) {
                        transferEncoding = new HttpString(transferEncodingHeader);
                    } else {
                        // RFC 2616 3.6 last paragraph
                        responseHeaders.remove(Headers.TRANSFER_ENCODING);
                    }
                } else if (exchange.isHttp11() && contentLengthHeader == null) {
                    //if we have a HTTP 1.1 request with no transfer encoding and no content length
                    //then we default to chunked, to enable persistent connections to work
                    responseHeaders.put(Headers.TRANSFER_ENCODING, Headers.CHUNKED.toString());
                    transferEncoding = Headers.CHUNKED;
                }
                StreamSinkConduit wrappedConduit;
                final int code = exchange.getResponseCode();
                if (exchange.getRequestMethod().equals(Methods.HEAD) || (100 <= code && code <= 199) || code == 204 || code == 304) {
                    final ConduitListener<StreamSinkConduit> finishListener = stillPersistent ? terminateResponseListener(exchange) : null;
                    if (code == 101 && contentLengthHeader != null) {
                        // add least for websocket upgrades we can have a content length
                        final long contentLength;
                        try {
                            contentLength = Long.parseLong(contentLengthHeader);
                            // fixed-length response
                            wrappedConduit = new FixedLengthStreamSinkConduit(channel, contentLength, true, !stillPersistent, finishListener);
                        } catch (NumberFormatException e) {
                            // assume that the response is unbounded, but forbid persistence (this will cause subsequent requests to fail when they write their replies)
                            stillPersistent = false;
                            wrappedConduit = new FinishableStreamSinkConduit(channel, terminateResponseListener(exchange));
                        }
                    } else {
                        wrappedConduit = new FixedLengthStreamSinkConduit(channel, 0L, true, !stillPersistent, finishListener);
                    }
                } else if (!transferEncoding.equals(Headers.IDENTITY)) {
                    final ConduitListener<StreamSinkConduit> finishListener = stillPersistent ? terminateResponseListener(exchange) : null;
                    wrappedConduit = new ChunkedStreamSinkConduit(channel, true, !stillPersistent, finishListener);
                } else if (contentLengthHeader != null) {
                    final long contentLength;
                    try {
                        contentLength = Long.parseLong(contentLengthHeader);
                        final ConduitListener<StreamSinkConduit> finishListener = stillPersistent ? terminateResponseListener(exchange) : null;
                        // fixed-length response
                        wrappedConduit = new FixedLengthStreamSinkConduit(channel, contentLength, true, !stillPersistent, finishListener);
                    } catch (NumberFormatException e) {
                        // assume that the response is unbounded, but forbid persistence (this will cause subsequent requests to fail when they write their replies)
                        stillPersistent = false;
                        wrappedConduit = new FinishableStreamSinkConduit(channel, terminateResponseListener(exchange));
                    }
                } else {
                    log.trace("Cancelling persistence because response is identity with no content length");
                    // make it not persistent - very unfortunate for the next request handler really...
                    stillPersistent = false;
                    wrappedConduit = new FinishableStreamSinkConduit(channel, terminateResponseListener(exchange));
                }
                if (code != 101) {
                    // only set connection header if it was not an upgrade
                    if (exchange.isHttp11()) {
                        if (stillPersistent) {
                            // not strictly required but user agents seem to like it
                            responseHeaders.put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString());
                        } else {
                            responseHeaders.put(Headers.CONNECTION, Headers.CLOSE.toString());
                        }
                    } else if (exchange.isHttp10()) {
                        if (stillPersistent) {
                            responseHeaders.put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString());
                        } else {
                            responseHeaders.remove(Headers.CONNECTION);
                        }
                    }
                }
                return wrappedConduit;
            }
        };
    }

    private static final ConduitWrapper<StreamSourceConduit> CHUNKED_STREAM_SOURCE_CONDUIT_WRAPPER = new ConduitWrapper<StreamSourceConduit>() {
            public StreamSourceConduit wrap(final ConduitFactory<StreamSourceConduit> factory, final HttpServerExchange exchange) {
                return new ChunkedStreamSourceConduit(factory.create(), exchange, chunkedDrainListener(exchange), maxEntitySize(exchange));
            }
        };

    private static ConduitWrapper<StreamSourceConduit> fixedLengthStreamSourceConduitWrapper(final long contentLength) {
        return new ConduitWrapper<StreamSourceConduit>() {
            public StreamSourceConduit wrap(final ConduitFactory<StreamSourceConduit> factory, final HttpServerExchange exchange) {
                StreamSourceConduit channel = factory.create();
                final long max = maxEntitySize(exchange);
                if(contentLength > max) {
                    return new BrokenStreamSourceConduit(channel, UndertowMessages.MESSAGES.requestEntityWasTooLarge(exchange.getSourceAddress(), max));
                }
                return new FixedLengthStreamSourceConduit(channel, contentLength, fixedLengthDrainListener(exchange));
            }
        };
    }

    private static final ConduitWrapper<StreamSourceConduit> EMPTY_STREAM_SOURCE_CONDUIT_WRAPPER = new ConduitWrapper<StreamSourceConduit>() {
            public StreamSourceConduit wrap(final ConduitFactory<StreamSourceConduit> factory, final HttpServerExchange exchange) {
                StreamSourceConduit channel = factory.create();
                return new EmptyStreamSourceConduit(channel.getReadThread());
            }
        };

    private static ConduitListener<FixedLengthStreamSourceConduit> fixedLengthDrainListener(final HttpServerExchange exchange) {
        return new ConduitListener<FixedLengthStreamSourceConduit>() {
            public void handleEvent(final FixedLengthStreamSourceConduit fixedLengthConduit) {
                long remaining = fixedLengthConduit.getRemaining();
                if (remaining > 0L) {
                    UndertowLogger.REQUEST_LOGGER.requestWasNotFullyConsumed();
                    exchange.setPersistent(false);
                }
                exchange.terminateRequest();
            }
        };
    }

    private static ConduitListener<ChunkedStreamSourceConduit> chunkedDrainListener(final HttpServerExchange exchange) {
        return new ConduitListener<ChunkedStreamSourceConduit>() {
            public void handleEvent(final ChunkedStreamSourceConduit chunkedStreamSourceConduit) {
                if(!chunkedStreamSourceConduit.isFinished()) {
                    UndertowLogger.REQUEST_LOGGER.requestWasNotFullyConsumed();
                    exchange.setPersistent(false);
                }
                exchange.terminateRequest();
            }
        };
    }

    private static ConduitListener<StreamSinkConduit> terminateResponseListener(final HttpServerExchange exchange) {
        return new ConduitListener<StreamSinkConduit>() {
            public void handleEvent(final StreamSinkConduit channel) {
                exchange.terminateResponse();
            }
        };
    }

    private static long maxEntitySize(final HttpServerExchange exchange) {
        return exchange.getAttachment(UndertowOptions.ATTACHMENT_KEY).get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
    }
}
