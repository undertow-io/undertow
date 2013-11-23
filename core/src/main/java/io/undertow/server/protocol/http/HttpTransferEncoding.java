/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

package io.undertow.server.protocol.http;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.conduits.ChunkedStreamSinkConduit;
import io.undertow.conduits.ChunkedStreamSourceConduit;
import io.undertow.conduits.ConduitListener;
import io.undertow.conduits.FinishableStreamSinkConduit;
import io.undertow.conduits.FinishableStreamSourceConduit;
import io.undertow.conduits.FixedLengthStreamSinkConduit;
import io.undertow.conduits.FixedLengthStreamSourceConduit;
import io.undertow.conduits.HeadStreamSinkConduit;
import io.undertow.conduits.PipeliningBufferingStreamSinkConduit;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.jboss.logging.Logger;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * Class that is  responsible for HTTP transfer encooding, this could be part of the {@link HttpReadListener},
 * but is separated out for clarity.
 *
 * For more info see http://tools.ietf.org/html/rfc2616#section-4.4
 *
 * @author Stuart Douglas
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class HttpTransferEncoding {

    private static final Logger log = Logger.getLogger("io.undertow.server.handler.transfer-encoding");

    /**
     * Construct a new instance.
     */
    private HttpTransferEncoding() {
    }

    public static void setupRequest(final HttpServerExchange exchange) {
        final HeaderMap requestHeaders = exchange.getRequestHeaders();
        final String connectionHeader = requestHeaders.getFirst(Headers.CONNECTION);
        final String transferEncodingHeader = requestHeaders.getLast(Headers.TRANSFER_ENCODING);
        final String contentLengthHeader = requestHeaders.getFirst(Headers.CONTENT_LENGTH);

        final HttpServerConnection connection = (HttpServerConnection) exchange.getConnection();
        ConduitStreamSinkChannel sinkChannel = connection.getChannel().getSinkChannel();
        //if we are already using the pipelineing buffer add it to the exchange
        PipeliningBufferingStreamSinkConduit pipeliningBuffer = connection.getAttachment(PipeliningBufferingStreamSinkConduit.ATTACHMENT_KEY);
        if (pipeliningBuffer != null) {
            pipeliningBuffer.setupPipelineBuffer(exchange);
        }
        ConduitStreamSourceChannel sourceChannel = connection.getChannel().getSourceChannel();
        sourceChannel.setConduit(new ReadDataStreamSourceConduit(sourceChannel.getConduit(), connection));

        boolean persistentConnection = persistentConnection(exchange, connectionHeader);

        if (exchange.getRequestMethod().equals(Methods.GET)) {
            if (persistentConnection
                    && connection.getExtraBytes() != null
                    && pipeliningBuffer == null
                    && connection.getUndertowOptions().get(UndertowOptions.BUFFER_PIPELINED_DATA, false)) {
                pipeliningBuffer = new PipeliningBufferingStreamSinkConduit(connection.getOriginalSinkConduit(), connection.getBufferPool());
                connection.putAttachment(PipeliningBufferingStreamSinkConduit.ATTACHMENT_KEY, pipeliningBuffer);
                pipeliningBuffer.setupPipelineBuffer(exchange);
            }
            // no content - immediately start the next request, returning an empty stream for this one
            Connectors.terminateRequest(exchange);
        } else {
            persistentConnection = handleRequestEncoding(exchange, transferEncodingHeader, contentLengthHeader, connection, pipeliningBuffer, persistentConnection);
        }

        exchange.setPersistent(persistentConnection);
        sinkChannel.setConduit(new HttpResponseConduit(sinkChannel.getConduit(), connection.getBufferPool(), exchange));

        //now the response wrapper, to add in the appropriate connection control headers
        exchange.addResponseWrapper(responseWrapper());

    }

    private static boolean handleRequestEncoding(final HttpServerExchange exchange, String transferEncodingHeader, String contentLengthHeader, HttpServerConnection connection, PipeliningBufferingStreamSinkConduit pipeliningBuffer, boolean persistentConnection) {
        HttpString transferEncoding = Headers.IDENTITY;
        if (transferEncodingHeader != null) {
            transferEncoding = new HttpString(transferEncodingHeader);
        }
        if (transferEncodingHeader != null && !transferEncoding.equals(Headers.IDENTITY)) {
            ConduitStreamSourceChannel sourceChannel = ((HttpServerConnection) exchange.getConnection()).getChannel().getSourceChannel();
            sourceChannel.setConduit(new ChunkedStreamSourceConduit(sourceChannel.getConduit(), exchange, chunkedDrainListener(exchange)));
        } else if (contentLengthHeader != null) {
            final long contentLength;
            contentLength = Long.parseLong(contentLengthHeader);
            if (contentLength == 0L) {
                log.trace("No content, starting next request");
                // no content - immediately start the next request, returning an empty stream for this one
                Connectors.terminateRequest(exchange);
            } else {
                // fixed-length content - add a wrapper for a fixed-length stream
                ConduitStreamSourceChannel sourceChannel = ((HttpServerConnection) exchange.getConnection()).getChannel().getSourceChannel();
                sourceChannel.setConduit(fixedLengthStreamSourceConduitWrapper(contentLength, sourceChannel.getConduit(), exchange));
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
                pipeliningBuffer = new PipeliningBufferingStreamSinkConduit(connection.getOriginalSinkConduit(), connection.getBufferPool());
                connection.putAttachment(PipeliningBufferingStreamSinkConduit.ATTACHMENT_KEY, pipeliningBuffer);
                pipeliningBuffer.setupPipelineBuffer(exchange);
            }

            // no content - immediately start the next request, returning an empty stream for this one
            Connectors.terminateRequest(exchange);
        } else if (exchange.isHttp11()) {
            //this is a http 1.1 non-persistent connection
            //we still know there is no content
            Connectors.terminateRequest(exchange);
        } else {
            ConduitStreamSourceChannel sourceChannel = ((HttpServerConnection) exchange.getConnection()).getChannel().getSourceChannel();
            sourceChannel.setConduit(new FinishableStreamSourceConduit(sourceChannel.getConduit(), new ConduitListener<FinishableStreamSourceConduit>() {
                @Override
                public void handleEvent(FinishableStreamSourceConduit channel) {
                    Connectors.terminateRequest(exchange);
                }
            }));
        }
        return persistentConnection;
    }

    private static boolean persistentConnection(HttpServerExchange exchange, String connectionHeader) {
        if (exchange.isHttp11()) {
            return !(connectionHeader != null && Headers.CLOSE.equalToString(connectionHeader));
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

    private static ConduitWrapper<StreamSinkConduit> responseWrapper() {
        return HttpResponseWrapper.INSTANCE;
    }


    private static StreamSourceConduit fixedLengthStreamSourceConduitWrapper(final long contentLength, final StreamSourceConduit conduit, final HttpServerExchange exchange) {
        return new FixedLengthStreamSourceConduit(conduit, contentLength, fixedLengthDrainListener(exchange), exchange);
    }

    private static ConduitListener<FixedLengthStreamSourceConduit> fixedLengthDrainListener(final HttpServerExchange exchange) {
        return new ConduitListener<FixedLengthStreamSourceConduit>() {
            public void handleEvent(final FixedLengthStreamSourceConduit fixedLengthConduit) {
                long remaining = fixedLengthConduit.getRemaining();
                if (remaining > 0L) {
                    UndertowLogger.REQUEST_LOGGER.requestWasNotFullyConsumed();
                    exchange.setPersistent(false);
                }
                Connectors.terminateRequest(exchange);
            }
        };
    }

    private static ConduitListener<ChunkedStreamSourceConduit> chunkedDrainListener(final HttpServerExchange exchange) {
        return new ConduitListener<ChunkedStreamSourceConduit>() {
            public void handleEvent(final ChunkedStreamSourceConduit chunkedStreamSourceConduit) {
                if (!chunkedStreamSourceConduit.isFinished()) {
                    UndertowLogger.REQUEST_LOGGER.requestWasNotFullyConsumed();
                    exchange.setPersistent(false);
                }
                Connectors.terminateRequest(exchange);
            }
        };
    }

    private static ConduitListener<StreamSinkConduit> terminateResponseListener(final HttpServerExchange exchange) {
        return new ConduitListener<StreamSinkConduit>() {
            public void handleEvent(final StreamSinkConduit channel) {
                Connectors.terminateResponse(exchange);
            }
        };
    }

    private static class HttpResponseWrapper implements ConduitWrapper<StreamSinkConduit> {

        public static final HttpResponseWrapper INSTANCE = new HttpResponseWrapper();


        public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
            StreamSinkConduit channel = factory.create();
            final ConduitListener<StreamSinkConduit> finishListener = terminateResponseListener(exchange);
            boolean headRequest = exchange.getRequestMethod().equals(Methods.HEAD);
            if (headRequest) {
                //if this is a head request we add a head channel underneath the content encoding channel
                //this will just discard the data
                //we still go through with the rest of the logic, to make sure all headers are set correctly
                channel = new HeadStreamSinkConduit(channel, finishListener);
            }

            final HeaderMap responseHeaders = exchange.getResponseHeaders();
            // test to see if we're still persistent
            String connection = responseHeaders.getFirst(Headers.CONNECTION);
            if(!exchange.isPersistent()) {
                responseHeaders.put(Headers.CONNECTION, Headers.CLOSE.toString());
            } else if (exchange.isPersistent() && connection != null) {
                if (HttpString.tryFromString(connection).equals(Headers.CLOSE)) {
                    exchange.setPersistent(false);
                }
            } else if (exchange.getConnection().getUndertowOptions().get(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true)) {
                responseHeaders.put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString());
            }

            final String contentLengthHeader = responseHeaders.getFirst(Headers.CONTENT_LENGTH);
            if (contentLengthHeader != null) {
                try {
                    final long contentLength = Long.parseLong(contentLengthHeader);
                    if(headRequest) {
                        return channel;
                    }
                    // fixed-length response
                    return new FixedLengthStreamSinkConduit(channel, contentLength, true, !exchange.isPersistent(), finishListener);
                } catch (NumberFormatException e) {
                    //we just fix it for them
                    responseHeaders.remove(Headers.CONTENT_LENGTH);
                }
            }
            final String transferEncodingHeader = responseHeaders.getLast(Headers.TRANSFER_ENCODING);
            if (transferEncodingHeader == null) {
                if (exchange.isHttp11()) {
                    responseHeaders.put(Headers.TRANSFER_ENCODING, Headers.CHUNKED.toString());

                    if(headRequest) {
                        return channel;
                    }
                    return new ChunkedStreamSinkConduit(channel, exchange.getConnection().getBufferPool(), true, !exchange.isPersistent(), responseHeaders, finishListener, exchange);
                } else {
                    exchange.setPersistent(false);
                    responseHeaders.put(Headers.CONNECTION, Headers.CLOSE.toString());
                    if(headRequest) {
                        return channel;
                    }
                    return new FinishableStreamSinkConduit(channel, finishListener);
                }
            } else {
                //moved outside because this is rarely used
                //and makes the method small enough to be inlined
                return handleExplicitTransferEncoding(exchange, channel, finishListener, responseHeaders, transferEncodingHeader, headRequest);
            }
        }

        private StreamSinkConduit handleExplicitTransferEncoding(HttpServerExchange exchange, StreamSinkConduit channel, ConduitListener<StreamSinkConduit> finishListener, HeaderMap responseHeaders, String transferEncodingHeader, boolean headRequest) {
            HttpString transferEncoding = new HttpString(transferEncodingHeader);
            if (transferEncoding.equals(Headers.CHUNKED)) {
                if(headRequest) {
                    return channel;
                }
                return new ChunkedStreamSinkConduit(channel, exchange.getConnection().getBufferPool(), true, !exchange.isPersistent(), responseHeaders, finishListener, exchange);
            } else {

                if(headRequest) {
                    return channel;
                }
                log.trace("Cancelling persistence because response is identity with no content length");
                // make it not persistent - very unfortunate for the next request handler really...
                exchange.setPersistent(false);
                responseHeaders.put(Headers.CONNECTION, Headers.CLOSE.toString());
                return new FinishableStreamSinkConduit(channel, terminateResponseListener(exchange));
            }
        }
    }
}
