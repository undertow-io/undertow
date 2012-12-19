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

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.undertow.UndertowMessages;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.XnioExecutor;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.Bits.allAreSet;
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

    private final HttpServerConnection connection;
    private final HeaderMap requestHeaders = new HeaderMap();
    private final HeaderMap responseHeaders = new HeaderMap();

    private final Map<String, Deque<String>> queryParameters = new HashMap<String, Deque<String>>(0);

    private final StreamSinkChannel underlyingResponseChannel;
    private final StreamSourceChannel underlyingRequestChannel;
    /**
     * The actual response channel. May be null if it has not been created yet.
     */
    private StreamSinkChannel responseChannel;

    private final Runnable requestTerminateAction;
    private final Runnable responseTerminateAction;

    private HttpString protocol;

    // mutable state

    private volatile int state = 200;
    private volatile HttpString requestMethod;
    private volatile String requestScheme;
    /**
     * The original request URI. This will include the host name if it was specified by the client
     */
    private volatile String requestURI;
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
    private volatile String resolvedPath = "";

    /**
     * the query string
     */
    private volatile String queryString;

    private boolean complete = false;

    private static final ChannelWrapper<StreamSourceChannel>[] NO_SOURCE_WRAPPERS = new ChannelWrapper[0];
    private static final ChannelWrapper<StreamSinkChannel>[] NO_SINK_WRAPPERS = new ChannelWrapper[0];

    private volatile ChannelWrapper[] requestWrappers = NO_SOURCE_WRAPPERS;
    private volatile ChannelWrapper[] responseWrappers = NO_SINK_WRAPPERS;

    private static final AtomicReferenceFieldUpdater<HttpServerExchange, ChannelWrapper[]> requestWrappersUpdater = AtomicReferenceFieldUpdater.newUpdater(HttpServerExchange.class, ChannelWrapper[].class, "requestWrappers");
    private static final AtomicReferenceFieldUpdater<HttpServerExchange, ChannelWrapper[]> responseWrappersUpdater = AtomicReferenceFieldUpdater.newUpdater(HttpServerExchange.class, ChannelWrapper[].class, "responseWrappers");

    private static final AtomicIntegerFieldUpdater<HttpServerExchange> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(HttpServerExchange.class, "state");

    private static final int MASK_RESPONSE_CODE = intBitMask(0, 9);
    private static final int FLAG_RESPONSE_SENT = 1 << 10;
    private static final int FLAG_RESPONSE_TERMINATED = 1 << 11;
    private static final int FLAG_REQUEST_TERMINATED = 1 << 12;
    private static final int FLAG_CLEANUP = 1 << 13;

    public HttpServerExchange(final HttpServerConnection connection, final StreamSourceChannel requestChannel, final StreamSinkChannel responseChannel, final Runnable requestTerminateAction, final Runnable responseTerminateAction) {
        this.connection = connection;
        this.underlyingRequestChannel = requestChannel;
        if(connection == null) {
            //just for unit tests
            this.underlyingResponseChannel = null;
        } else {
            this.underlyingResponseChannel = responseChannel;
        }
        this.requestTerminateAction = requestTerminateAction;
        this.responseTerminateAction = responseTerminateAction;
    }

    /**
     * Get the request protocol string.  Normally this is one of the strings listed in {@link Protocols}.
     *
     * @return the request protocol string
     */
    public HttpString getProtocol() {
        return protocol;
    }

    /**
     * Sets the http protocol
     *
     * @param protocol
     */
    public void setProtocol(final HttpString protocol) {
        this.protocol = protocol;
    }

    /**
     * Determine whether this request conforms to HTTP 0.9.
     *
     * @return {@code true} if the request protocol is equal to {@link Protocols#HTTP_0_9}, {@code false} otherwise
     */
    public boolean isHttp09() {
        return protocol.equals(Protocols.HTTP_0_9);
    }

    /**
     * Determine whether this request conforms to HTTP 1.0.
     *
     * @return {@code true} if the request protocol is equal to {@link Protocols#HTTP_1_0}, {@code false} otherwise
     */
    public boolean isHttp10() {
        return protocol.equals(Protocols.HTTP_1_0);
    }

    /**
     * Determine whether this request conforms to HTTP 1.1.
     *
     * @return {@code true} if the request protocol is equal to {@link Protocols#HTTP_1_1}, {@code false} otherwise
     */
    public boolean isHttp11() {
        return protocol.equals(Protocols.HTTP_1_1);
    }

    /**
     * Get the HTTP request method.  Normally this is one of the strings listed in {@link Methods}.
     *
     * @return the HTTP request method
     */
    public HttpString getRequestMethod() {
        return requestMethod;
    }

    /**
     * Set the HTTP request method.
     *
     * @param requestMethod the HTTP request method
     */
    public void setRequestMethod(final HttpString requestMethod) {
        this.requestMethod = requestMethod;
    }

    /**
     * Get the request URI scheme.  Normally this is one of {@code http} or {@code https}.
     *
     * @return the request URI scheme
     */
    public String getRequestScheme() {
        return requestScheme;
    }

    /**
     * Set the request URI scheme.
     *
     * @param requestScheme the request URI scheme
     */
    public void setRequestScheme(final String requestScheme) {
        this.requestScheme = requestScheme;
    }

    /**
     * Gets the request URI, including hostname, protocol etc if specified by the client.
     * <p/>
     * In most cases this will be equal to {@link #requestPath}
     *
     * @return The request URI
     */
    public String getRequestURI() {
        return requestURI;
    }

    /**
     * Sets the request URI
     *
     * @param requestURI The new request URI
     */
    public void setRequestURI(final String requestURI) {
        this.requestURI = requestURI;
    }

    /**
     * Get the request URI path.  This is the whole original request path.
     *
     * @return the request URI path
     */
    public String getRequestPath() {
        return requestPath;
    }

    /**
     * Set the request URI path.
     *
     * @param requestPath the request URI path
     */
    public void setRequestPath(final String requestPath) {
        this.requestPath = requestPath;
    }

    /**
     * Get the request relative path.  This is the path which should be evaluated by the current handler.
     * <p/>
     * If the {@link io.undertow.server.handlers.CanonicalPathHandler} is installed in the current chain
     * then this path with be canonicalized
     *
     * @return the request relative path
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * Set the request relative path.
     *
     * @param relativePath the request relative path
     */
    public void setRelativePath(final String relativePath) {
        this.relativePath = relativePath;
    }

    /**
     * internal method used by the parser to set both the request and relative
     * path fields
     */
    void setParsedRequestPath(final String requestPath) {
        this.relativePath = requestPath;
        this.requestPath = requestPath;
    }

    /**
     * Get the resolved path.
     *
     * @return the resolved path
     */
    public String getResolvedPath() {
        return resolvedPath;
    }

    /**
     * Set the resolved path.
     *
     * @param resolvedPath the resolved path
     */
    public void setResolvedPath(final String resolvedPath) {
        this.resolvedPath = resolvedPath;
    }

    /**
     * Get the canonical path.
     *
     * @return the canonical path
     */
    public String getCanonicalPath() {
        return canonicalPath;
    }

    /**
     * Set the canonical path.
     *
     * @param canonicalPath the canonical path
     */
    public void setCanonicalPath(final String canonicalPath) {
        this.canonicalPath = canonicalPath;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(final String queryString) {
        this.queryString = queryString;
    }

    /**
     * Reconstructs the complete URL as seen by the user. This includes scheme, host name etc,
     * but does not include query string.
     */
    public String getRequestURL() {
        String host = getRequestHeaders().getFirst(Headers.HOST);
        if (host == null) {
            host = getDestinationAddress().getAddress().getHostAddress();
        }
        return getRequestScheme() + "://" + host + getRequestURI();
    }

    /**
     * Get the underlying HTTP connection.
     *
     * @return the underlying HTTP connection
     */
    public HttpServerConnection getConnection() {
        return connection;
    }

    /**
     * Upgrade the channel to a raw socket. This method set the response code to 101, and then marks both the
     * request and response as terminated, which means that once the current request is completed the raw channel
     * can be obtained from {@link io.undertow.server.HttpServerConnection#getChannel()}
     *
     * @throws IllegalStateException if a response or upgrade was already sent, or if the request body is already being
     *                               read
     */
    public void upgradeChannel(){
        setResponseCode(101);

        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED)) {
                // idempotent
                return;
            }
            newVal = oldVal | FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED;
        } while (!stateUpdater.compareAndSet(this, oldVal, newVal));
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
    public Map<String, Deque<String>> getQueryParameters() {
        return queryParameters;
    }

    public void addQueryParam(final String name, final String param) {
        Deque<String> list = queryParameters.get(name);
        if (list == null) {
            queryParameters.put(name, list = new ArrayDeque<String>());
        }
        list.add(param);
    }

    /**
     * @return <code>true</code> If the response has already been started
     */
    public boolean isResponseStarted() {
        return allAreSet(state, FLAG_RESPONSE_SENT);
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
            final StreamSourceChannel oldChannel = channel;
            channel = ((ChannelWrapper<StreamSourceChannel>) wrapper).wrap(oldChannel, this);
            if (channel == null) {
                channel = oldChannel;
            }
        }
        return channel;
    }

    public boolean isRequestChannelAvailable() {
        return requestWrappers != null;
    }

    /**
     * Returns true if the completion handler for this exchange has been invoked, and the request is considered
     * finished.
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Force the codec to treat the request as fully read.  Should only be invoked by handlers which downgrade
     * the socket or implement a transfer coding.
     */
    public void terminateRequest() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_REQUEST_TERMINATED)) {
                // idempotent
                return;
            }
            newVal = oldVal | FLAG_REQUEST_TERMINATED;
        } while (!stateUpdater.compareAndSet(this, oldVal, newVal));
        requestTerminateAction.run();
    }

    /**
     * Get the factory to produce the response channel.  The resultant channel's {@link StreamSinkChannel#close()} or
     * {@link StreamSinkChannel#shutdownWrites()} method must be called at some point after the request is processed to
     * prevent resource leakage and to allow the next request to proceed.  Closing a fixed-length response before the
     * corresponding number of bytes has been written will cause the connection to be reset and subsequent requests to
     * fail; thus it is important to ensure that the proper content length is delivered when one is specified.  The
     * response channel may not be writable until after the response headers have been sent.
     * <p/>
     * If this method is not called then an empty or default response body will be used, depending on the response code set.
     * <p/>
     * The returned channel will begin to write out headers when the first write request is initiated, or when
     * {@link java.nio.channels.Channel#close()} is called on the channel with no content being written.  Once the channel
     * is acquired, however, the response code and headers may not be modified.
     *
     * @return the response channel factory, or {@code null} if another party already acquired the channel factory
     */
    public ChannelFactory<StreamSinkChannel> getResponseChannelFactory() {
        final ChannelWrapper[] wrappers = responseWrappersUpdater.getAndSet(this, null);
        if (wrappers == null) {
            return null;
        }
        return new ResponseChannelFactory(this, underlyingResponseChannel, wrappers);
    }

    private static final class ResponseChannelFactory implements ChannelFactory<StreamSinkChannel> {
        private static final AtomicReferenceFieldUpdater<ResponseChannelFactory, ChannelWrapper[]> wrappersUpdater = AtomicReferenceFieldUpdater.newUpdater(ResponseChannelFactory.class, ChannelWrapper[].class, "wrappers");
        private final HttpServerExchange exchange;
        private final StreamSinkChannel firstChannel;
        @SuppressWarnings("unused")
        private volatile ChannelWrapper[] wrappers;

        ResponseChannelFactory(final HttpServerExchange exchange, final StreamSinkChannel firstChannel, final ChannelWrapper[] wrappers) {
            this.exchange = exchange;
            this.firstChannel = firstChannel;
            this.wrappers = wrappers;
        }

        public StreamSinkChannel create() {
            final ChannelWrapper[] wrappers = wrappersUpdater.getAndSet(this, null);
            if (wrappers == null) {
                return null;
            }
            StreamSinkChannel oldChannel = firstChannel;
            StreamSinkChannel channel = oldChannel;
            for (ChannelWrapper wrapper : wrappers) {
                channel = ((ChannelWrapper<StreamSinkChannel>) wrapper).wrap(channel, exchange);
                if (channel == null) {
                    channel = oldChannel;
                }
            }
            exchange.responseChannel = channel;
            exchange.startResponse();
            return channel;
        }
    }

    /**
     * @return <code>true</code> if {@link #getResponseChannelFactory()} has not been called
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
            oldVal = state;
            if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
                throw UndertowMessages.MESSAGES.responseAlreadyStarted();
            }
            newVal = oldVal & ~MASK_RESPONSE_CODE | responseCode & MASK_RESPONSE_CODE;
        } while (!stateUpdater.compareAndSet(this, oldVal, newVal));
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
        return state & MASK_RESPONSE_CODE;
    }

    /**
     * Force the codec to treat the response as fully written.  Should only be invoked by handlers which downgrade
     * the socket or implement a transfer coding.
     */
    void terminateResponse() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_RESPONSE_TERMINATED)) {
                // idempotent
                return;
            }
            newVal = oldVal | FLAG_RESPONSE_TERMINATED;
        } while (!stateUpdater.compareAndSet(this, oldVal, newVal));
        if (responseTerminateAction != null) {
            responseTerminateAction.run();
        }
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
            oldVal = state;
            if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
                throw UndertowMessages.MESSAGES.responseAlreadyStarted();
            }
            newVal = oldVal | FLAG_RESPONSE_SENT;
        } while (!stateUpdater.compareAndSet(this, oldVal, newVal));

        log.tracef("Starting to write response for %s using channel %s", this, underlyingResponseChannel);
        final HeaderMap responseHeaders = this.responseHeaders;
        responseHeaders.lock();
    }


    public void cleanup() {
        // All other cleanup handlers have been called.  We will inspect the state of the exchange
        // and attempt to fix any leftover or broken crap as best as we can.
        //
        // At this point if any channels were not acquired, we know that not even default handlers have
        // handled the request, meaning we basically have no idea what their state is; the response headers
        // may not even be valid.
        //
        // The only thing we can do is to determine if the request and reply were both terminated; if not,
        // consume the request body nicely, send whatever HTTP response we have, and close down the connection.
        complete = true;
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_CLEANUP)) {
                return;
            }
            newVal = oldVal | FLAG_CLEANUP | FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED;
        } while (!stateUpdater.compareAndSet(this, oldVal, newVal));
        final StreamSourceChannel requestChannel = underlyingRequestChannel;
        StreamSinkChannel responseChannel = this.responseChannel;
        if(responseChannel == null) {
            responseChannel = getResponseChannelFactory().create();
        }
        if (allAreSet(oldVal, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED)) {
            // we're good; a transfer coding handler took care of things.
            return;
        } else {
            try {
                //we do not attempt to drain the read side, as one of the reasons this could
                //be happening is because the request was too large
                requestChannel.shutdownReads();
                responseChannel.shutdownWrites();
                if (!responseChannel.flush()) {
                    responseChannel.getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            // this shouldn't be necessary...
                            channel.suspendWrites();
                            channel.getWriteSetter().set(null);
                        }
                    }, ChannelListeners.closingChannelExceptionHandler()));
                    responseChannel.resumeWrites();
                }
            } catch (Throwable t) {
                // All sorts of things could go wrong, from runtime exceptions to java.io.IOException to errors.
                // Just kill off the connection, it's fucked beyond repair.
                safeClose(requestChannel);
                safeClose(responseChannel);
                safeClose(connection);
            }
        }
    }

    public XnioExecutor getWriteThread() {
        return underlyingResponseChannel.getWriteThread();
    }

    public XnioExecutor getReadThread() {
        return underlyingRequestChannel.getReadThread();
    }
}
