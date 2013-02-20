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

package io.undertow.client;

import io.undertow.UndertowLogger;
import io.undertow.channels.GatedStreamSinkChannel;
import io.undertow.conduits.ChunkedStreamSinkConduit;
import io.undertow.conduits.ConduitListener;
import io.undertow.conduits.FinishableStreamSinkConduit;
import io.undertow.conduits.FixedLengthStreamSinkConduit;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.StreamSinkChannelWrappingConduit;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channel;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Emanuel Muckenhuber
 */
class HttpClientRequestImpl extends HttpClientRequest {

    private final URI target;
    private final boolean http11;
    private final HttpString method;
    private final HttpString protocol;

    private final boolean pipeline;
    private final OptionMap options;
    private final GatedStreamSinkChannel underlyingChannel;
    private final HttpClientConnectionImpl connection;
    private final FutureResult<HttpClientResponse> responseFuture = new FutureResult<HttpClientResponse>();

    private StreamSinkChannel requestChannel;
    private boolean hasContent;

    private static final Set<HttpString> idempotentMethods = new HashSet<>();
    static {
        idempotentMethods.add(Methods.GET);
        idempotentMethods.add(Methods.HEAD);
        idempotentMethods.add(Methods.PUT);
        idempotentMethods.add(Methods.DELETE);
        idempotentMethods.add(Methods.OPTIONS);
        idempotentMethods.add(Methods.TRACE);
    }

    HttpClientRequestImpl(final HttpClientConnectionImpl connection, final StreamSinkChannel underlyingChannel, final String method, final URI target, final boolean pipeline) {
        super(connection);
        this.options = connection.getOptions();

        this.method = new HttpString(method);
        this.target = target;
        this.connection = connection;
        this.underlyingChannel = new GatedStreamSinkChannel(underlyingChannel, this, false, false);
        this.protocol = options.get(HttpClientOptions.PROTOCOL, Protocols.HTTP_1_1);
        this.http11 = Protocols.HTTP_1_1.equals(protocol);
        this.pipeline = pipeline;
    }

    @Override
    public String getMethod() {
        return method.toString();
    }

    @Override
    public URI getTarget() {
        return target;
    }

    @Override
    public String getProtocol() {
        return protocol.toString();
    }

    @Override
    public IoFuture<HttpClientResponse> getResponse() {
        return responseFuture.getIoFuture();
    }

    String getURIString() {
        try {
            return new URI(null, null, null, -1, target.getPath(), target.getQuery(), target.getFragment()).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public StreamSinkChannel writeRequestBody(long contentLength) throws IOException {
        // Prepare the header
        final HeaderMap headers = getRequestHeaders();
        boolean keepAlive;
        if (http11) {
            if(headers.contains(Headers.CONNECTION)) {
                keepAlive = headers.get(Headers.CONNECTION).equals(Headers.KEEP_ALIVE.toString());
            } else {
                keepAlive = true;
            }
        } else if (Protocols.HTTP_1_0.equals(protocol)) {
            keepAlive = options.get(HttpClientOptions.HTTP_KEEP_ALIVE, false);
        } else {
            keepAlive = false;
        }

        HttpString contentEncoding = Headers.IDENTITY;
        boolean hasContent = true;
        if (contentLength == -1L) {
            // unknown content-length
            if(Methods.HEAD.equals(method)) {
                hasContent = false;
            } else if (! http11) {
                keepAlive = false;
            } else {
                contentEncoding = Headers.CHUNKED;
            }
        } else if (contentLength == 0L) {
            hasContent = false;
        } else if (contentLength <= 0L) {
            throw new IllegalArgumentException("invalid content-length");
        }
        if(hasContent) {
            if(Methods.HEAD.equals(method)) {
                hasContent = false;
            }
        }
        if(keepAlive) {
            headers.put(Headers.CONNECTION, Headers.KEEP_ALIVE.toString());
        } else {
            headers.put(Headers.CONNECTION, Headers.CLOSE.toString());
        }
        if(! headers.contains(Headers.HOST)) {
            String host = null;
            if(target.isAbsolute()) {
                host = target.getHost();
            }
            if(host == null) {
                try {
                    host = connection.getPeerAddress(InetSocketAddress.class).getHostString();
                } catch (Exception ignore)  {
                    //
                }
            }
            if(host != null) {
                headers.put(Headers.HOST, host);
            } else if(http11) {
                headers.put(Headers.HOST, "");
            }
        }
        // Create the request and channel
        final boolean pipelineNext = pipeline && idempotentMethods.contains(method);
        final PendingHttpRequest request = new PendingHttpRequest(this, connection, keepAlive, pipelineNext, responseFuture);
        StreamSinkConduit conduit = new StreamSinkChannelWrappingConduit(underlyingChannel);
        conduit = new HttpRequestConduit(conduit, connection.getBufferPool(), this);
        if(! hasContent) {
            headers.put(Headers.CONTENT_LENGTH, 0L);
            conduit = new FixedLengthStreamSinkConduit(conduit, 0L, false, ! keepAlive, completedListener(request), null);
        } else {
            if (! Headers.IDENTITY.equals(contentEncoding)) {
                headers.put(Headers.TRANSFER_ENCODING, Headers.CHUNKED.toString());
                conduit = new ChunkedStreamSinkConduit(conduit, false, ! keepAlive, completedListener(request));
            } else {
                if(contentLength == -1L) {
                    conduit = new FinishableStreamSinkConduit(conduit, completedListener(request));
                } else {
                    headers.put(Headers.CONTENT_LENGTH, contentLength);
                    conduit = new FixedLengthStreamSinkConduit(conduit, contentLength, false, ! keepAlive, completedListener(request), null);
                }
            }
        }
        headers.lock();
        this.requestChannel = new ConduitStreamSinkChannel(underlyingChannel, conduit);
        this.hasContent = hasContent;
        // Enqueue the request for sending
        connection.enqueueRequest(request);
        return requestChannel;
    }

    public boolean startSending(PendingHttpRequest pendingHttpRequest) {
        underlyingChannel.openGate(this);
        if(! hasContent) {
            // Flush directly if it has no content
            try {
                if (!requestChannel.flush()) {
                    requestChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                            new ChannelListener<StreamSinkChannel>() {
                                @Override
                                public void handleEvent(final StreamSinkChannel channel) {
                                    channel.suspendWrites();
                                    channel.getWriteSetter().set(null);
                                }
                            }, new ChannelExceptionHandler<Channel>() {
                                @Override
                                public void handleException(final Channel channel, final IOException exception) {
                                    UndertowLogger.CLIENT_LOGGER.debug("Exception ending request", exception);
                                    IoUtils.safeClose(connection.getChannel());
                                }
                            }
                    ));
                    requestChannel.resumeWrites();
                } else {
                    return true;
                }
            } catch(IOException e) {
                UndertowLogger.CLIENT_LOGGER.debug("Exception sending request", e);
                IoUtils.safeClose(connection.getChannel());
            }
        }
        return false;
    }

    private ConduitListener<? super StreamSinkConduit> completedListener(final PendingHttpRequest request) {
        return new ConduitListener<StreamSinkConduit>() {
            @Override
            public void handleEvent(StreamSinkConduit channel) {
                request.requestSent();
            }
        };
    }

    @Override
    public String toString() {
        return "HttpClientRequestImpl{" + method + " " + target + " " + protocol + '}';
    }
}
