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
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.jboss.logging.Logger;
import org.xnio.conduits.EmptyStreamSourceConduit;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

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
    public void handleRequest(final HttpServerExchange exchange) {
        final HeaderMap requestHeaders = exchange.getRequestHeaders();
        boolean persistentConnection;
        final boolean hasConnectionHeader = requestHeaders.contains(Headers.CONNECTION);
        final boolean hasTransferEncoding = requestHeaders.contains(Headers.TRANSFER_ENCODING);
        final boolean hasContentLength = requestHeaders.contains(Headers.CONTENT_LENGTH);
        if (exchange.isHttp11()) {
            persistentConnection = !(hasConnectionHeader && new HttpString(requestHeaders.getFirst(Headers.CONNECTION)).equals(Headers.CLOSE));
        } else if (exchange.isHttp10()) {
            persistentConnection = false;
            if (hasConnectionHeader) {
                for (String value : requestHeaders.get(Headers.CONNECTION)) {
                    if (Headers.KEEP_ALIVE.equals(new HttpString(value))) {
                        persistentConnection = true;
                        break;
                    }
                }
            }
        } else {
            log.trace("Connection not persistent");
            persistentConnection = false;
        }
        HttpString transferEncoding = Headers.IDENTITY;
        if (hasTransferEncoding) {
            transferEncoding = new HttpString(requestHeaders.getLast(Headers.TRANSFER_ENCODING));
        }
        if (hasTransferEncoding && !transferEncoding.equals(Headers.IDENTITY)) {
            exchange.addRequestWrapper(chunkedStreamSourceConduitWrapper());
        } else if (hasContentLength) {
            final long contentLength;
            try {
                contentLength = Long.parseLong(requestHeaders.get(Headers.CONTENT_LENGTH).getFirst());
            } catch (NumberFormatException e) {
                log.trace("Invalid request due to unparsable content length");
                // content length is bad; invalid request
                exchange.setResponseCode(400);
                exchange.endExchange();
                return;
            }
            if (contentLength == 0L) {
                log.trace("No content, starting next request");
                // no content - immediately start the next request, returning an empty stream for this one
                exchange.addRequestWrapper(emptyStreamSourceConduitWrapper());
                exchange.terminateRequest();
            } else {
                // fixed-length content - add a wrapper for a fixed-length stream
                exchange.addRequestWrapper(fixedLengthStreamSourceConduitWrapper(contentLength));
            }
        } else if (hasTransferEncoding) {
            if (transferEncoding.equals(Headers.IDENTITY)) {
                log.trace("Connection not persistent (no content length and identity transfer encoding)");
                // make it not persistent
                persistentConnection = false;
            }
        } else if (persistentConnection) {
            // no content - immediately start the next request, returning an empty stream for this one
            exchange.terminateRequest();
            exchange.addRequestWrapper(emptyStreamSourceConduitWrapper());
        }

        exchange.setPersistent(persistentConnection);

        //now the response wrapper, to add in the appropriate connection control headers
        exchange.addResponseWrapper(responseWrapper(persistentConnection));
        HttpHandlers.executeHandler(next, exchange);
    }

    private static ConduitWrapper<StreamSinkConduit> responseWrapper(final boolean requestLooksPersistent) {
        return new ConduitWrapper<StreamSinkConduit>() {
            public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
                final StreamSinkConduit channel = factory.create();
                final HeaderMap responseHeaders = exchange.getResponseHeaders();
                // test to see if we're still persistent
                boolean stillPersistent = requestLooksPersistent;
                HttpString transferEncoding = Headers.IDENTITY;
                if (responseHeaders.contains(Headers.TRANSFER_ENCODING)) {
                    if (exchange.isHttp11()) {
                        transferEncoding = new HttpString(responseHeaders.getLast(Headers.TRANSFER_ENCODING));
                    } else {
                        // RFC 2616 3.6 last paragraph
                        responseHeaders.remove(Headers.TRANSFER_ENCODING);
                    }
                } else if (exchange.isHttp11() && !responseHeaders.contains(Headers.CONTENT_LENGTH)) {
                    //if we have a HTTP 1.1 request with no transfer encoding and no content length
                    //then we default to chunked, to enable persistent connections to work
                    responseHeaders.put(Headers.TRANSFER_ENCODING, Headers.CHUNKED.toString());
                    transferEncoding = Headers.CHUNKED;
                }
                StreamSinkConduit wrappedConduit;
                final int code = exchange.getResponseCode();
                if (exchange.getRequestMethod().equals(Methods.HEAD) || (100 <= code && code <= 199) || code == 204 || code == 304) {
                    final ConduitListener<StreamSinkConduit> finishListener = stillPersistent ? terminateResponseListener(exchange) : null;
                    if (code == 101 && responseHeaders.contains(Headers.CONTENT_LENGTH)) {
                        // add least for websocket upgrades we can have a content length
                        final long contentLength;
                        try {
                            contentLength = Long.parseLong(responseHeaders.get(Headers.CONTENT_LENGTH).getFirst());
                            // fixed-length response
                            wrappedConduit = new FixedLengthStreamSinkConduit(channel, contentLength, true, !stillPersistent, finishListener, null);
                        } catch (NumberFormatException e) {
                            // assume that the response is unbounded, but forbid persistence (this will cause subsequent requests to fail when they write their replies)
                            stillPersistent = false;
                            wrappedConduit = new FinishableStreamSinkConduit(channel, terminateResponseListener(exchange));
                        }
                    } else {
                        wrappedConduit = new FixedLengthStreamSinkConduit(channel, 0L, true, !stillPersistent, finishListener, null);
                    }
                } else if (!transferEncoding.equals(Headers.IDENTITY)) {
                    final ConduitListener<StreamSinkConduit> finishListener = stillPersistent ? terminateResponseListener(exchange) : null;
                    wrappedConduit = new ChunkedStreamSinkConduit(channel, true, !stillPersistent, finishListener);
                } else if (responseHeaders.contains(Headers.CONTENT_LENGTH)) {
                    final long contentLength;
                    try {
                        contentLength = Long.parseLong(responseHeaders.get(Headers.CONTENT_LENGTH).getFirst());
                        final ConduitListener<StreamSinkConduit> finishListener = stillPersistent ? terminateResponseListener(exchange) : null;
                        // fixed-length response
                        wrappedConduit = new FixedLengthStreamSinkConduit(channel, contentLength, true, !stillPersistent, finishListener, null);
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

    private static ConduitWrapper<StreamSourceConduit> chunkedStreamSourceConduitWrapper() {
        return new ConduitWrapper<StreamSourceConduit>() {
            public StreamSourceConduit wrap(final ConduitFactory<StreamSourceConduit> factory, final HttpServerExchange exchange) {
                return new ChunkedStreamSourceConduit(factory.create(), exchange, chunkedDrainListener(exchange), maxEntitySize(exchange));
            }
        };
    }

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

    private static ConduitWrapper<StreamSourceConduit> emptyStreamSourceConduitWrapper() {
        return new ConduitWrapper<StreamSourceConduit>() {
            public StreamSourceConduit wrap(final ConduitFactory<StreamSourceConduit> factory, final HttpServerExchange exchange) {
                StreamSourceConduit channel = factory.create();
                return new EmptyStreamSourceConduit(channel.getReadThread());
            }
        };
    }

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

    private static long maxEntitySize(final HttpServerExchange exchange) {
        return exchange.getAttachment(UndertowOptions.ATTACHMENT_KEY).get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
    }
}
