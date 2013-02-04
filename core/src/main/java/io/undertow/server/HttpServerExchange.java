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
import java.nio.channels.Channel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.ImmediateChannelFactory;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.SecureHashMap;
import org.jboss.logging.Logger;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.XnioExecutor;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.intBitMask;

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

    private List<ExchangeCompletionListener> exchangeCompleteListeners = new ArrayList<ExchangeCompletionListener>(1);
    private Deque<DefaultResponseListener> defaultResponseListeners = new ArrayDeque<DefaultResponseListener>(1);

    private Map<String, Deque<String>> queryParameters;

    private final StreamSinkChannel underlyingResponseChannel;
    private final StreamSourceChannel underlyingRequestChannel;
    /**
     * The actual response channel. May be null if it has not been created yet.
     */
    private StreamSinkChannel responseChannel;
    /**
     * The actual request channel. May be null if it has not been created yet.
     */
    private StreamSourceChannel requestChannel;

    private HttpString protocol;

    // mutable state

    private int state = 200;
    private HttpString requestMethod;
    private String requestScheme;
    /**
     * The original request URI. This will include the host name if it was specified by the client
     */
    private String requestURI;
    /**
     * The original request path.
     */
    private String requestPath;
    /**
     * The canonical version of the original path.
     */
    private String canonicalPath;
    /**
     * The remaining unresolved portion of the canonical path.
     */
    private String relativePath;

    /**
     * The resolved part of the canonical path.
     */
    private String resolvedPath = "";

    /**
     * the query string
     */
    private String queryString;

    private List<ChannelWrapper<StreamSourceChannel>> requestWrappers = new ArrayList<ChannelWrapper<StreamSourceChannel>>(3);
    private List<ChannelWrapper<StreamSinkChannel>> responseWrappers = new ArrayList<ChannelWrapper<StreamSinkChannel>>(3);

    private static final int MASK_RESPONSE_CODE = intBitMask(0, 9);
    private static final int FLAG_RESPONSE_SENT = 1 << 10;
    private static final int FLAG_RESPONSE_TERMINATED = 1 << 11;
    private static final int FLAG_REQUEST_TERMINATED = 1 << 12;
    private static final int FLAG_PERSISTENT = 1 << 14;

    public HttpServerExchange(final HttpServerConnection connection, final StreamSourceChannel requestChannel, final StreamSinkChannel responseChannel) {
        this.connection = connection;
        this.underlyingRequestChannel = requestChannel;
        if(connection == null) {
            //just for unit tests
            this.underlyingResponseChannel = null;
        } else {
            this.underlyingResponseChannel = responseChannel;
        }
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

    public boolean isPersistent() {
        return anyAreSet(state, FLAG_PERSISTENT);
    }

    public boolean isUpgrade() {
        return getResponseCode() == 101;
    }

    public void setPersistent(final boolean persistent) {
        if(persistent) {
            this.state = this.state | FLAG_PERSISTENT;
        } else {
            this.state = this.state & ~FLAG_PERSISTENT;
        }
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
        int oldVal = state;
        if(connection.getPipeLiningBuffer() != null) {
            connection.getPipeLiningBuffer().upgradeUnderlyingChannel();
        }
    }

    /**
     * Upgrade the channel to a raw socket. This method set the response code to 101, and then marks both the
     * request and response as terminated, which means that once the current request is completed the raw channel
     * can be obtained from {@link io.undertow.server.HttpServerConnection#getChannel()}
     *
     * @param productName the product name to report to the client
     * @throws IllegalStateException if a response or upgrade was already sent, or if the request body is already being
     *                               read
     */
    public void upgradeChannel(String productName) {
        setResponseCode(101);
        final HeaderMap headers = getResponseHeaders();
        headers.add(Headers.UPGRADE, productName);
        headers.add(Headers.CONNECTION, Headers.UPGRADE_STRING);
        if(connection.getPipeLiningBuffer() != null) {
            connection.getPipeLiningBuffer().upgradeUnderlyingChannel();
        }
    }

    public void addExchangeCompleteListener(final ExchangeCompletionListener listener){
        exchangeCompleteListeners.add(listener);
    }

    public void addDefaultResponseListener(final DefaultResponseListener listener){
        defaultResponseListeners.add(listener);
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
     * Returns a immutable map of very parameters.
     *
     * @return The query parameters
     */
    public Map<String, Deque<String>> getQueryParameters() {
        if(queryParameters == null) {
            return Collections.emptyMap();
        } else {
            return Collections.unmodifiableMap(queryParameters);
        }
    }

    public void addQueryParam(final String name, final String param) {
        if(queryParameters == null) {
            queryParameters = new SecureHashMap<String, Deque<String>>();
        }
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
        final List<ChannelWrapper<StreamSourceChannel>> wrappers = this.requestWrappers;
        this.requestWrappers = null;
        if (wrappers == null) {
            return null;
        }


        ChannelFactory<StreamSourceChannel> factory = new ImmediateChannelFactory<StreamSourceChannel>(underlyingRequestChannel);
        for (final ChannelWrapper<StreamSourceChannel> wrapper : wrappers) {
            final ChannelFactory oldFactory = factory;
            factory = new ChannelFactory<StreamSourceChannel>() {
                @Override
                public StreamSourceChannel create() {
                    return wrapper.wrap(oldFactory, HttpServerExchange.this);
                }
            };
        }
        return requestChannel = factory.create();
    }

    public boolean isRequestChannelAvailable() {
        return requestWrappers != null;
    }

    /**
     * Returns true if the completion handler for this exchange has been invoked, and the request is considered
     * finished.
     */
    public boolean isComplete() {
        return allAreSet(state, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED);
    }

    /**
     * Force the codec to treat the request as fully read.  Should only be invoked by handlers which downgrade
     * the socket or implement a transfer coding.
     */
    public void terminateRequest() {
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_REQUEST_TERMINATED)) {
            // idempotent
            return;
        }
        this.state = oldVal | FLAG_REQUEST_TERMINATED;
        if(anyAreSet(oldVal, FLAG_RESPONSE_TERMINATED)) {
            boolean upgrade = getResponseCode() == 101;
            for(ExchangeCompletionListener listener : exchangeCompleteListeners) {
                listener.exchangeEvent(this);
            }
        }
    }

    /**
     * Get the response channel.  The resultant channel's {@link StreamSinkChannel#close()} or
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
     * @return the response channel, or {@code null} if another party already acquired the channel
     */
    public StreamSinkChannel getResponseChannel() {
        final List<ChannelWrapper<StreamSinkChannel>> wrappers = responseWrappers;
        this.responseWrappers = null;
        if (wrappers == null) {
            return null;
        }

        ChannelFactory<StreamSinkChannel> factory = new ImmediateChannelFactory<StreamSinkChannel>(underlyingResponseChannel);
        for (final ChannelWrapper<StreamSinkChannel> wrapper : wrappers) {
            final ChannelFactory oldFactory = factory;
            factory = new ChannelFactory<StreamSinkChannel>() {
                @Override
                public StreamSinkChannel create() {
                    return wrapper.wrap(oldFactory, HttpServerExchange.this);
                }
            };
        }
        final StreamSinkChannel channel = factory.create();
        this.responseChannel = channel;
        this.startResponse();
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
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
            throw UndertowMessages.MESSAGES.responseAlreadyStarted();
        }
        this.state = oldVal & ~MASK_RESPONSE_CODE | responseCode & MASK_RESPONSE_CODE;
    }

    /**
     * Adds a {@link ChannelWrapper} to the request wrapper chain.
     *
     * @param wrapper the wrapper
     */
    public void addRequestWrapper(final ChannelWrapper<StreamSourceChannel> wrapper) {
        List<ChannelWrapper<StreamSourceChannel>> wrappers = requestWrappers;
        if (wrappers == null) {
            throw UndertowMessages.MESSAGES.requestChannelAlreadyProvided();
        }
        wrappers.add(wrapper);
    }

    /**
     * Adds a {@link ChannelWrapper} to the response wrapper chain.
     *
     * @param wrapper the wrapper
     */
    public void addResponseWrapper(final ChannelWrapper<StreamSinkChannel> wrapper) {
        List<ChannelWrapper<StreamSinkChannel>> wrappers = responseWrappers;
        if (wrappers == null) {
            throw UndertowMessages.MESSAGES.requestChannelAlreadyProvided();
        }
        wrappers.add(wrapper);
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
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_TERMINATED)) {
            // idempotent
            return;
        }
        this.state = oldVal | FLAG_RESPONSE_TERMINATED;
        if(anyAreSet(oldVal, FLAG_REQUEST_TERMINATED)) {
            boolean upgrade = getResponseCode() == 101;
            for(ExchangeCompletionListener listener : exchangeCompleteListeners) {
                listener.exchangeEvent(this);
            }
        }
    }

    /**
     * Ends the exchange by fully draining the request channel, and flushing the response channel.
     * <p/>
     * This can result in handoff to an XNIO worker, so after this method is called the exchange should
     * not be modified by the caller.
     * <p/>
     * If the exchange is already complete this method is a noop
     */
    public void endExchange() {
        while (!defaultResponseListeners.isEmpty()) {
            DefaultResponseListener listener = defaultResponseListeners.poll();
            try {
                if (listener.handleDefaultResponse(this)) {
                    return;
                }
            } catch (Exception e) {
                UndertowLogger.REQUEST_LOGGER.debug("Exception running default response listener", e);
            }
        }

        final int state = this.state;
        try {
            if (anyAreClear(state, FLAG_REQUEST_TERMINATED) && isPersistent()) {
                if (isRequestChannelAvailable()) {
                    getRequestChannel();
                }
                long res;
                do {
                    res = Channels.drain(requestChannel, Long.MAX_VALUE);
                    if (res == 0) {
                        requestChannel.getReadSetter().set(ChannelListeners.drainListener(Long.MAX_VALUE, new ChannelListener<StreamSourceChannel>() {
                                    @Override
                                    public void handleEvent(final StreamSourceChannel channel) {
                                        channel.suspendReads();
                                        channel.getReadSetter().set(null);
                                        closeAndFlushResponse();
                                    }
                                }, new ChannelExceptionHandler<StreamSourceChannel>() {
                                    @Override
                                    public void handleException(final StreamSourceChannel channel, final IOException exception) {
                                        UndertowLogger.REQUEST_LOGGER.debug("Exception ending request", exception);
                                        IoUtils.safeClose(connection.getChannel());
                                    }
                                }
                        ));
                        requestChannel.resumeReads();
                        return;
                    }
                } while (res > 0);
            }
            if (anyAreClear(state, FLAG_RESPONSE_TERMINATED)) {
                closeAndFlushResponse();
            }
        } catch (IOException e) {
            UndertowLogger.REQUEST_LOGGER.debug("Exception ending request", e);
            IoUtils.safeClose(connection.getChannel());
        }
    }

    private void closeAndFlushResponse() {
        try {
            if (isResponseChannelAvailable()) {
                getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
                getResponseChannel();
            }
            if (responseChannel.isOpen()) {
                responseChannel.shutdownWrites();
                if (!responseChannel.flush()) {
                    responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                            new ChannelListener<StreamSinkChannel>() {
                                @Override
                                public void handleEvent(final StreamSinkChannel channel) {
                                    channel.suspendWrites();
                                    channel.getWriteSetter().set(null);
                                }
                            }, new ChannelExceptionHandler<Channel>() {
                                @Override
                                public void handleException(final Channel channel, final IOException exception) {
                                    UndertowLogger.REQUEST_LOGGER.debug("Exception ending request", exception);
                                    IoUtils.safeClose(connection.getChannel());
                                }
                            }
                    ));
                    responseChannel.resumeWrites();
                }
            }
        } catch (IOException e) {
            UndertowLogger.REQUEST_LOGGER.debug("Exception ending request", e);
            IoUtils.safeClose(connection.getChannel());
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
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
            throw UndertowMessages.MESSAGES.responseAlreadyStarted();
        }
        this.state = oldVal | FLAG_RESPONSE_SENT;

        log.tracef("Starting to write response for %s using channel %s", this, underlyingResponseChannel);
        final HeaderMap responseHeaders = this.responseHeaders;
        responseHeaders.lock();
    }

    public XnioExecutor getWriteThread() {
        return underlyingResponseChannel.getWriteThread();
    }

    public XnioExecutor getReadThread() {
        return underlyingRequestChannel.getReadThread();
    }
}
