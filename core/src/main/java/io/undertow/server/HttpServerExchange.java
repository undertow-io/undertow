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
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.undertow.UndertowMessages;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.GatedStreamSinkChannel;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.Protocols;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.AssembledConnectedStreamChannel;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.channels.SuspendableReadChannel;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.intBitMask;
import static org.xnio.IoUtils.safeClose;

/**
 * An HTTP server request/response exchange.  An instance of this class is constructed as soon as the request headers are
 * fully parsed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpServerExchange extends AbstractAttachable {
    // immutable state

    private static final Logger log = Logger.getLogger(HttpServerExchange.class);

    private static final boolean traceEnabled;

    static {
        traceEnabled = log.isTraceEnabled();
    }

    @SuppressWarnings("unused") // todo for now
    private final HttpServerConnection connection;
    private final HeaderMap requestHeaders;
    private final HeaderMap responseHeaders;

    private final Map<String, List<String>> queryParameters;

    private final GatedStreamSinkChannel gatedResponseChannel;
    private final StreamSinkChannel underlyingResponseChannel;
    private final StreamSourceChannel underlyingRequestChannel;

    private final Object gatePermit = new Object();

    // todo: protocol should be immutable
    private volatile String protocol;

    // mutable state

    private volatile int responseState = 200;
    private volatile String requestMethod;
    private volatile String requestScheme;
    /**
     * The original request path.
     */
    private volatile String requestPath;
    /**
     * The canonical version of the original path.
     */
    private volatile String canonicalPath;
    /**
     * The remaining unresolved portion of the canonical path.
     */
    private volatile String relativePath;

    /**
     * The resolved part of the canonical path.
     */
    private volatile String resolvedPath = "/";

    private static final ChannelWrapper<StreamSourceChannel>[] NO_SOURCE_WRAPPERS = new ChannelWrapper[0];
    private static final ChannelWrapper<StreamSinkChannel>[] NO_SINK_WRAPPERS = new ChannelWrapper[0];

    private volatile ChannelWrapper[] requestWrappers = NO_SOURCE_WRAPPERS;
    private volatile ChannelWrapper[] responseWrappers = NO_SINK_WRAPPERS;

    private static final AtomicReferenceFieldUpdater<HttpServerExchange, ChannelWrapper[]> requestWrappersUpdater = AtomicReferenceFieldUpdater.newUpdater(HttpServerExchange.class, ChannelWrapper[].class, "requestWrappers");
    private static final AtomicReferenceFieldUpdater<HttpServerExchange, ChannelWrapper[]> responseWrappersUpdater = AtomicReferenceFieldUpdater.newUpdater(HttpServerExchange.class, ChannelWrapper[].class, "responseWrappers");

    private static final AtomicIntegerFieldUpdater<HttpServerExchange> responseStateUpdater = AtomicIntegerFieldUpdater.newUpdater(HttpServerExchange.class, "responseState");

    private static final int MASK_RESPONSE_CODE = intBitMask(0, 9);
    private static final int FLAG_RESPONSE_SENT = 1 << 10;
    private static final int FLAG_RESPONSE_TERMINATED = 1 << 11;
    private static final int FLAG_REQUEST_TERMINATED = 1 << 12;
    private static final int FLAG_CLEANUP = 1 << 13;

    protected HttpServerExchange(final HttpServerConnection connection, final HeaderMap requestHeaders, final HeaderMap responseHeaders, final Map<String, List<String>> queryParameters, final String requestMethod, final StreamSourceChannel requestChannel, final StreamSinkChannel responseChannel) {
        this.connection = connection;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.queryParameters = queryParameters;
        this.requestMethod = requestMethod;
        this.underlyingRequestChannel = requestChannel;
        this.underlyingResponseChannel = responseChannel;
        this.gatedResponseChannel = new GatedStreamSinkChannel(responseChannel, gatePermit, false, false);
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(final String protocol) {
        this.protocol = protocol;
    }

    public boolean isHttp09() {
        return protocol.equals(Protocols.HTTP_0_9);
    }

    public boolean isHttp10() {
        return protocol.equals(Protocols.HTTP_1_0);
    }

    public boolean isHttp11() {
        return protocol.equals(Protocols.HTTP_1_1);
    }

    /**
     * If this is not a HTTP/1.1 request, or if the Connection: close header was sent we need to close the channel
     *
     * @return <code>true</code> if the connection should be closed once the response has been written
     */
    boolean isCloseConnection() {
        if (!isHttp11()) {
            return true;
        }
        final Deque<String> connection = requestHeaders.get(Headers.CONNECTION);
        if (connection != null) {
            for (final String value : connection) {
                if (value.toLowerCase().equals("close")) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(final String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestScheme() {
        return requestScheme;
    }

    public void setRequestScheme(final String requestScheme) {
        this.requestScheme = requestScheme;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(final String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(final String relativePath) {
        this.relativePath = relativePath;
    }

    public String getResolvedPath() {
        return resolvedPath;
    }

    public void setResolvedPath(final String resolvedPath) {
        this.resolvedPath = resolvedPath;
    }

    public String getCanonicalPath() {
        return canonicalPath;
    }

    public void setCanonicalPath(final String canonicalPath) {
        this.canonicalPath = canonicalPath;
    }

    public HttpServerConnection getConnection() {
        return connection;
    }

    /**
     * Upgrade the channel to a raw socket.  This is a convenience method which sets a 101 response code, sends the
     * response headers, and merges the request and response channels into one full-duplex socket stream channel.
     *
     * @return the socket channel
     * @throws IllegalStateException if a response or upgrade was already sent, or if the request body is already being
     *                               read
     */
    public ConnectedStreamChannel upgradeChannel() throws IllegalStateException, IOException {
        setResponseCode(101);
        return new AssembledConnectedStreamChannel(getRequestChannel(), getResponseChannel());
    }

    /**
     * Get the source address of the HTTP request.
     *
     * @return the source address of the HTTP request
     */
    public InetSocketAddress getSourceAddress() {
        return connection.getPeerAddress(InetSocketAddress.class);
    }

    /**
     * Get the destination address of the HTTP request.
     *
     * @return the destination address of the HTTP request
     */
    public InetSocketAddress getDestinationAddress() {
        return connection.getLocalAddress(InetSocketAddress.class);
    }

    /**
     * Get the request headers.
     *
     * @return the request headers
     */
    public HeaderMap getRequestHeaders() {
        return requestHeaders;
    }

    /**
     * Get the response headers.
     *
     * @return the response headers
     */
    public HeaderMap getResponseHeaders() {
        return responseHeaders;
    }

    /**
     * Returns a mutable map of very parameters.
     *
     * @return The query parameters
     */
    public Map<String, List<String>> getQueryParameters() {
        return queryParameters;
    }

    /**
     * @return <code>true</code> If the response has already been started
     */
    public boolean isResponseStarted() {
        return allAreSet(responseState, FLAG_RESPONSE_SENT);
    }

    /**
     * Get the inbound request.  If there is no request body, calling this method
     * may cause the next request to immediately be processed.  The {@link StreamSourceChannel#close()} or {@link StreamSourceChannel#shutdownReads()}
     * method must be called at some point after the request is processed to prevent resource leakage and to allow
     * the next request to proceed.  Any unread content will be discarded.
     *
     * @return the channel for the inbound request, or {@code null} if another party already acquired the channel
     */
    public StreamSourceChannel getRequestChannel() {
        final ChannelWrapper[] wrappers = requestWrappersUpdater.getAndSet(this, null);
        if (wrappers == null) {
            return null;
        }
        StreamSourceChannel channel = underlyingRequestChannel;
        for (ChannelWrapper wrapper : wrappers) {
            channel = ((ChannelWrapper<StreamSourceChannel>) wrapper).wrap(channel, this);
            if (channel == null) {
                throw UndertowMessages.MESSAGES.failedToAcquireRequestChannel();
            }
        }
        return channel;
    }

    public boolean isRequestChannelAvailable() {
        return requestWrappers != null;
    }

    StreamSourceChannel getUnderlyingRequestChannel() {
        return underlyingRequestChannel;
    }

    /**
     * Force the codec to treat the request as fully read.  Should only be invoked by handlers which downgrade
     * the socket or implement a transfer coding.
     */
    void terminateRequest() {
    }

    /**
     * Get the response channel. The {@link StreamSinkChannel#close()} or {@link StreamSinkChannel#shutdownWrites()}
     * method must be called at some point after the request is processed to prevent resource leakage and to allow
     * the next request to proceed.  Closing a fixed-length response before the corresponding number of bytes has
     * been written will cause the connection to be reset and subsequent requests to fail; thus it is important to
     * ensure that the proper content length is delivered when one is specified.  The response channel may not be
     * writable until after the response headers have been sent.
     * <p/>
     * If this method is not called then an empty or default response body will be used, depending on the response code set.
     * <p/>
     * The returned stream will lazily begin to write out headers when the first write request is initiated, or when
     * {@link java.nio.channels.Channel#close()} is called on the channel with no content being written.
     *
     * @return the response channel, or {@code null} if another party already acquired the channel
     */
    public StreamSinkChannel getResponseChannel() {
        final ChannelWrapper[] wrappers = responseWrappersUpdater.getAndSet(this, null);
        if (wrappers == null) {
            return null;
        }
        StreamSinkChannel channel = new HttpResponseChannel(gatedResponseChannel, connection.getBufferPool(), this);
        for (ChannelWrapper wrapper : wrappers) {
            channel = ((ChannelWrapper<StreamSinkChannel>) wrapper).wrap(channel, this);
            if (channel == null) {
                throw UndertowMessages.MESSAGES.failedToAcquireResponseChannel();
            }
        }
        return channel;
    }

    /**
     * @return <code>true</code> if {@link #getResponseChannel()} has not been called
     */
    public boolean isResponseChannelAvailable() {
        return responseWrappers != null;
    }

    /**
     * Change the response code for this response.  If not specified, the code will be a {@code 200}.  Setting
     * the response code after the response headers have been transmitted has no effect.
     *
     * @param responseCode the new code
     * @throws IllegalStateException if a response or upgrade was already sent
     */
    public void setResponseCode(final int responseCode) {
        if (responseCode < 0 || responseCode > 999) {
            throw new IllegalArgumentException("Invalid response code");
        }
        int oldVal, newVal;
        do {
            oldVal = responseState;
            if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
                throw UndertowMessages.MESSAGES.responseAlreadyStarted();
            }
            newVal = oldVal & ~MASK_RESPONSE_CODE | responseCode & MASK_RESPONSE_CODE;
        } while (!responseStateUpdater.compareAndSet(this, oldVal, newVal));
    }

    /**
     * Adds a {@link ChannelWrapper} to the request wrapper chain.
     *
     * @param wrapper the wrapper
     */
    public void addRequestWrapper(final ChannelWrapper<StreamSourceChannel> wrapper) {
        ChannelWrapper[] oldVal;
        ChannelWrapper[] newVal;
        int oldLen;
        do {
            oldVal = requestWrappers;
            if (oldVal == null) {
                throw UndertowMessages.MESSAGES.requestChannelAlreadyProvided();
            }
            oldLen = oldVal.length;
            newVal = Arrays.copyOf(oldVal, oldLen + 1);
            newVal[oldLen] = wrapper;
        } while (!requestWrappersUpdater.compareAndSet(this, oldVal, newVal));
    }

    /**
     * Adds a {@link ChannelWrapper} to the response wrapper chain.
     *
     * @param wrapper the wrapper
     */
    public void addResponseWrapper(final ChannelWrapper<StreamSinkChannel> wrapper) {
        ChannelWrapper[] oldVal;
        ChannelWrapper[] newVal;
        int oldLen;
        do {
            oldVal = responseWrappers;
            if (oldVal == null) {
                throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
            }
            oldLen = oldVal.length;
            newVal = Arrays.copyOf(oldVal, oldLen + 1);
            newVal[oldLen] = wrapper;
        } while (!responseWrappersUpdater.compareAndSet(this, oldVal, newVal));
    }

    /**
     * Get the response code.
     *
     * @return the response code
     */
    public int getResponseCode() {
        return responseState & MASK_RESPONSE_CODE;
    }

    /**
     * Force the codec to treat the response as fully written.  Should only be invoked by handlers which downgrade
     * the socket or implement a transfer coding.
     */
    void terminateResponse() {
        int oldVal, newVal;
        do {
            oldVal = responseState;
            if (allAreSet(oldVal, FLAG_RESPONSE_TERMINATED)) {
                // idempotent
                return;
            }
            newVal = oldVal | FLAG_RESPONSE_TERMINATED;
        } while (!responseStateUpdater.compareAndSet(this, oldVal, newVal));
        // todo - let next exchange start pushing headers
    }

    /**
     * Transmit the response headers. After this method successfully returns,
     * the response channel may become writable.
     * <p/>
     * If this method fails the request and response channels will be closed.
     * <p/>
     * This method runs asynchronously. If the channel is writable it will
     * attempt to write as much of the response header as possible, and then
     * queue the rest in a listener and return.
     * <p/>
     * If future handlers in the chain attempt to write before this is finished
     * XNIO will just magically sort it out so it works. This is not actually
     * implemented yet, so we just terminate the connection straight away at
     * the moment.
     * <p/>
     * TODO: make this work properly
     *
     * @throws IllegalStateException if the response headers were already sent
     */
    void startResponse() throws IllegalStateException {
        int oldVal, newVal;
        do {
            oldVal = responseState;
            if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
                throw UndertowMessages.MESSAGES.responseAlreadyStarted();
            }
            newVal = oldVal | FLAG_RESPONSE_SENT;
        } while (!responseStateUpdater.compareAndSet(this, oldVal, newVal));

        if (traceEnabled) {
            log.tracef("Starting to write response for %s using channel %s", this, underlyingResponseChannel);
        }
        final HeaderMap responseHeaders = this.responseHeaders;
        responseHeaders.lock();
        // todo un-gate
    }


    void cleanup() {
        // All other cleanup handlers have been called.  We will inspect the state of the exchange
        // and attempt to fix any leftover or broken crap as best as we can.
        //
        // At this point if any channels were not acquired, we know that not even default handlers have
        // handled the request, meaning we basically have no idea what their state is; the response headers
        // may not even be valid.
        //
        // The only thing we can do is to determine if the request and reply were both terminated; if not,
        // consume the request body nicely, send whatever HTTP response we have, and close down the connection.
        int oldVal, newVal;
        do {
            oldVal = responseState;
            if (allAreSet(oldVal, FLAG_CLEANUP)) {
                return;
            }
            newVal = oldVal | FLAG_CLEANUP | FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED;
        } while (!responseStateUpdater.compareAndSet(this, oldVal, newVal));
        if (allAreClear(oldVal, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED)) {
            try {
                // Attempt a nice shutdown.
                long res;
                do {
                    res = Channels.drain(underlyingRequestChannel, Long.MAX_VALUE);
                } while (res > 0);
                if (res == 0) {
                    underlyingRequestChannel.getReadSetter().set(ChannelListeners.<StreamSourceChannel>drainListener(Long.MAX_VALUE, new ChannelListener<SuspendableReadChannel>() {
                        public void handleEvent(final SuspendableReadChannel channel) {
                            IoUtils.safeShutdownReads(channel);
                        }
                    }, ChannelListeners.closingChannelExceptionHandler()));
                    underlyingRequestChannel.resumeReads();
                }
                gatedResponseChannel.shutdownWrites();
                if (!gatedResponseChannel.flush()) {
                    gatedResponseChannel.getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, ChannelListeners.closingChannelExceptionHandler()));
                    gatedResponseChannel.resumeWrites();
                }
            } catch (Throwable t) {
                // All sorts of things could go wrong, from runtime exceptions to java.io.IOException to errors.
                // Just kill off the connection, it's fucked beyond repair.
                safeClose(underlyingRequestChannel);
                safeClose(gatedResponseChannel);
                safeClose(underlyingResponseChannel);
                safeClose(connection);
            }
        } else if (anyAreClear(oldVal, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED)) {
            // Only one of the two channels were terminated - this is bad, because it means
            // we may well have just closed one half of our socket but not the other.  Just
            // kill the socket.
            safeClose(underlyingRequestChannel);
            safeClose(gatedResponseChannel);
            safeClose(connection);
        }
        // Otherwise we're good; a transfer coding handler took care of things.
    }
}
