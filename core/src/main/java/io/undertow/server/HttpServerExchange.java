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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.io.AsyncSenderImpl;
import io.undertow.io.BlockingSenderImpl;
import io.undertow.io.Sender;
import io.undertow.io.UndertowInputStream;
import io.undertow.io.UndertowOutputStream;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.NetworkUtils;
import io.undertow.util.Protocols;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.WrapperConduitFactory;
import org.jboss.logging.Logger;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.Channels;
import org.xnio.channels.EmptyStreamSourceChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

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

    /**
     * The executor that is to be used to dispatch the {@link #DISPATCH_TASK}. Note that this is not cleared
     * between dispatches, so once a request has been dispatched once then all subsequent dispatches will use
     * the same executor.
     */
    public static final AttachmentKey<Executor> DISPATCH_EXECUTOR = AttachmentKey.create(Executor.class);

    /**
     * When the call stack return this task will be executed by the executor specified in {@link #DISPATCH_EXECUTOR}.
     * If the executor is null then it will be executed by the XNIO worker.
     */
    public static final AttachmentKey<Runnable> DISPATCH_TASK = AttachmentKey.create(Runnable.class);

    private static final Logger log = Logger.getLogger(HttpServerExchange.class);

    private final HttpServerConnection connection;
    private final HeaderMap requestHeaders = new HeaderMap();
    private final HeaderMap responseHeaders = new HeaderMap();

    private int exchangeCompletionListenersCount = 0;
    private ExchangeCompletionListener[] exchangeCompleteListeners = new ExchangeCompletionListener[2];
    private final Deque<DefaultResponseListener> defaultResponseListeners = new ArrayDeque<DefaultResponseListener>(1);

    private Map<String, Deque<String>> queryParameters;
    private Map<String, Deque<String>> pathParameters;

    private Map<String, Cookie> requestCookies;
    private Map<String, Cookie> responseCookies;

    /**
     * The actual response channel. May be null if it has not been created yet.
     */
    private WriteDispatchChannel responseChannel;
    /**
     * The actual request channel. May be null if it has not been created yet.
     */
    private ReadDispatchChannel requestChannel;

    private BlockingHttpExchange blockingHttpExchange;

    private HttpString protocol;

    // mutable state

    private int state = 200;
    private HttpString requestMethod;
    private String requestScheme;

    /**
     * The original request URI. This will include the host name if it was specified by the client.
     * <p/>
     * This is not decoded in any way, and does not include the query string.
     * <p/>
     * Examples:
     * GET http://localhost:8080/myFile.jsf?foo=bar HTTP/1.1 -> 'http://localhost:8080/myFile.jsf'
     * POST /my+File.jsf?foo=bar HTTP/1.1 -> '/my+File.jsf'
     */
    private String requestURI;

    /**
     * The request path. This will be decoded by the server, and does not include the query string.
     * <p/>
     * This path is not canonicalised, so care must be taken to ensure that escape attacks are not possible.
     * <p/>
     * Examples:
     * GET http://localhost:8080/b/../my+File.jsf?foo=bar HTTP/1.1 -> '/b/../my+File.jsf'
     * POST /my+File.jsf?foo=bar HTTP/1.1 -> '/my File.jsf'
     */
    private String requestPath;

    /**
     * The remaining unresolved portion of request path. If a {@link io.undertow.server.handlers.CanonicalPathHandler} is
     * installed this will be canonicalised.
     * <p/>
     * Initially this will be equal to {@link #requestPath}, however it will be modified as handlers resolve the path.
     */
    private String relativePath;

    /**
     * The resolved part of the canonical path.
     */
    private String resolvedPath = "";

    /**
     * the query string
     */
    private String queryString = "";

    private int requestWrapperCount = 0;
    private ConduitWrapper<StreamSourceConduit>[] requestWrappers; //we don't allocate these by default, as for get requests they are not used

    private int responseWrapperCount = 0;
    private ConduitWrapper<StreamSinkConduit>[] responseWrappers = new ConduitWrapper[4]; //these are allocated by default, as they are always used

    private Sender sender;

    private static final int MASK_RESPONSE_CODE = intBitMask(0, 9);

    /**
     * Flag that is set when the response sending begins
     */
    private static final int FLAG_RESPONSE_SENT = 1 << 10;

    /**
     * Flag that is sent when the response has been fully written and flushed.
     */
    private static final int FLAG_RESPONSE_TERMINATED = 1 << 11;

    /**
     * Flag that is set once the request has been fully read. For zero
     * length requests this is set immediately.
     */
    private static final int FLAG_REQUEST_TERMINATED = 1 << 12;

    /**
     * Flag that is set if this is a persistent connection, and the
     * connection should be re-used.
     */
    private static final int FLAG_PERSISTENT = 1 << 14;

    /**
     * If this flag is set it means that the request has been dispatched,
     * and will not be ending when the call stack returns.
     * <p/>
     * This could be because it is being dispatched to a worker thread from
     * an IO thread, or because resume(Reads/Writes) has been called.
     */
    private static final int FLAG_DISPATCHED = 1 << 15;

    /**
     * Flag that is set if the {@link #requestURI} field contains the hostname.
     */
    private static final int FLAG_URI_CONTAINS_HOST = 1 << 16;

    /**
     * If this flag is set then the request is current running through a
     * handler chain.
     * <p/>
     * This will be true most of the time, this only time this will return
     * false is when performing async operations outside the scope of a call to
     * {@link HttpHandlers#executeRootHandler(HttpHandler, HttpServerExchange, boolean)},
     * such as when performing async IO.
     * <p/>
     * If this is true then when the call stack returns the exchange will either be dispatched,
     * or the exchange will be ended.
     */
    private static final int FLAG_IN_CALL = 1 << 17;

    public HttpServerExchange(final HttpServerConnection connection) {
        this.connection = connection;
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
     * Get the HTTP request method.  Normally this is one of the strings listed in {@link io.undertow.util.Methods}.
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
     * The original request URI. This will include the host name, protocol etc
     * if it was specified by the client.
     * <p/>
     * This is not decoded in any way, and does not include the query string.
     * <p/>
     * Examples:
     * GET http://localhost:8080/myFile.jsf?foo=bar HTTP/1.1 -> 'http://localhost:8080/myFile.jsf'
     * POST /my+File.jsf?foo=bar HTTP/1.1 -> '/my+File.jsf'
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
     * If a request was submitted to the server with a full URI instead of just a path this
     * will return true. For example:
     * <p/>
     * GET http://localhost:8080/b/../my+File.jsf?foo=bar HTTP/1.1 -> true
     * POST /my+File.jsf?foo=bar HTTP/1.1 -> false
     *
     * @return <code>true</code> If the request URI contains the host part of the URI
     */
    public boolean isHostIncludedInRequestURI() {
        return anyAreSet(state, FLAG_URI_CONTAINS_HOST);
    }


    /**
     * The request path. This will be decoded by the server, and does not include the query string.
     * <p/>
     * This path is not canonicalised, so care must be taken to ensure that escape attacks are not possible.
     * <p/>
     * Examples:
     * GET http://localhost:8080/b/../my+File.jsf?foo=bar HTTP/1.1 -> '/b/../my+File.jsf'
     * POST /my+File.jsf?foo=bar HTTP/1.1 -> '/my File.jsf'
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
    void setParsedRequestPath(final boolean requestUriContainsHost, final String requestUri, final String requestPath) {
        this.requestURI = requestUri;
        this.relativePath = requestPath;
        this.requestPath = requestPath;
        if (requestUriContainsHost) {
            state |= FLAG_URI_CONTAINS_HOST;
        }
    }

    void setParsedRequestPath(final String requestPath) {
        this.relativePath = requestPath;
        this.requestPath = requestPath;
    }

    void setParsedRequestPath(final boolean requestUriContainsHost, final String requestUri) {
        this.requestURI = requestUri;
        if (requestUriContainsHost) {
            state |= FLAG_URI_CONTAINS_HOST;
        }
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

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(final String queryString) {
        this.queryString = queryString;
    }

    /**
     * Reconstructs the complete URL as seen by the user. This includes scheme, host name etc,
     * but does not include query string.
     *
     * This is not decoded.
     *
     */
    public String getRequestURL() {
        if (isHostIncludedInRequestURI()) {
            return getRequestURI();
        } else {
            return getRequestScheme() + "://" + getHostAndPort() + getRequestURI();
        }
    }

    /**
     * Return the host that this request was sent to, in general this will be the
     * value of the Host header, minus the port specifier.
     *
     * If this resolves to an IPv6 address it will not be enclosed by square brackets.
     * Care must be taken when constructing URLs based on this method to ensure IPv6 URLs
     * are handled correctly.
     *
     * @return The host part of the destination address
     */
    public String getHostName() {
        String host = requestHeaders.getFirst(Headers.HOST);
        if (host == null) {
            host = getDestinationAddress().getAddress().getHostAddress();
        } else {
            if (host.startsWith("[")) {
                host = host.substring(1, host.indexOf(']'));
            } else if (host.indexOf(':') != -1) {
                host = host.substring(0, host.indexOf(':'));
            }
        }
        return host;
    }

    /**
     * Return the host, and also the port if this request was sent to a non-standard port. In general
     * this will just be the value of the Host header.
     *
     * If this resolves to an IPv6 address it *will*  be enclosed by square brackets. The return
     * value of this method is suitable for inclusion in a URL.
     *
     * @return The host and port part of the destination address
     */
    public String getHostAndPort() {
        String host = requestHeaders.getFirst(Headers.HOST);
        if (host == null) {
            host = NetworkUtils.formatPossibleIpv6Address(getDestinationAddress().getAddress().getHostAddress());
            int port = getDestinationAddress().getPort();
            if (!((getRequestScheme().equals("http") && port == 80)
                    || (getRequestScheme().equals("https") && port == 8080))) {
                host = host + ":" + port;
            }
        }
        return host;
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

    public boolean isInIoThread() {
        return getIoThread() == Thread.currentThread();
    }

    public boolean isUpgrade() {
        return getResponseCode() == 101;
    }

    public void setPersistent(final boolean persistent) {
        if (persistent) {
            this.state = this.state | FLAG_PERSISTENT;
        } else {
            this.state = this.state & ~FLAG_PERSISTENT;
        }
    }

    public boolean isDispatched() {
        return anyAreSet(state, FLAG_DISPATCHED);
    }

    public void unDispatch() {
        state &= ~FLAG_DISPATCHED;
        removeAttachment(DISPATCH_EXECUTOR);
        removeAttachment(DISPATCH_TASK);
    }

    /**
     *
     */
    public void dispatch() {
        state |= FLAG_DISPATCHED;
    }

    /**
     * Dispatches this request to the XNIO worker thread pool. Once the call stack returns
     * the given runnable will be submitted to the executor.
     * <p/>
     * In general handlers should first check the value of {@link #isInIoThread()} before
     * calling this method, and only dispatch if the request is actually running in the IO
     * thread.
     *
     * @param runnable The task to run
     * @throws IllegalStateException If this exchange has already been dispatched
     */
    public void dispatch(final Runnable runnable) {
        dispatch(null, runnable);
    }

    /**
     * Dispatches this request to the given executor. Once the call stack returns
     * the given runnable will be submitted to the executor.
     * <p/>
     * In general handlers should first check the value of {@link #isInIoThread()} before
     * calling this method, and only dispatch if the request is actually running in the IO
     * thread.
     *
     * @param runnable The task to run
     * @throws IllegalStateException If this exchange has already been dispatched
     */
    public void dispatch(final Executor executor, final Runnable runnable) {
        if (isInCall()) {
            state |= FLAG_DISPATCHED;
            if (executor != null) {
                putAttachment(DISPATCH_EXECUTOR, executor);
            }
            putAttachment(DISPATCH_TASK, runnable);
        } else {
            if (executor == null) {
                getConnection().getWorker().execute(runnable);
            } else {
                executor.execute(runnable);
            }
        }
    }

    public void dispatch(final HttpHandler handler) {
        dispatch(null, handler);
    }

    public void dispatch(final Executor executor, final HttpHandler handler) {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                HttpHandlers.executeRootHandler(handler, HttpServerExchange.this, false);
            }
        };
        dispatch(executor, runnable);
    }

    /**
     * Sets the executor that is used for dispatch operations where no executor is specified.
     *
     * @param executor The executor to use
     */
    public void setDispatchExecutor(final Executor executor) {
        if (executor == null) {
            removeAttachment(DISPATCH_EXECUTOR);
        } else {
            putAttachment(DISPATCH_EXECUTOR, executor);
        }
    }

    /**
     * Gets the current executor that is used for dispatch operations. This may be null
     *
     * @return The current dispatch executor
     */
    public Executor getDispatchExecutor() {
        return getAttachment(DISPATCH_EXECUTOR);
    }

    boolean isInCall() {
        return anyAreSet(state, FLAG_IN_CALL);
    }

    void setInCall(boolean value) {
        if (value) {
            state |= FLAG_IN_CALL;
        } else {
            state &= ~FLAG_IN_CALL;
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
    public void upgradeChannel(final ExchangeCompletionListener upgradeCompleteListener) {
        setResponseCode(101);
        final int exchangeCompletionListenersCount = this.exchangeCompletionListenersCount++;
        ExchangeCompletionListener[] exchangeCompleteListeners = this.exchangeCompleteListeners;
        if (exchangeCompleteListeners.length == exchangeCompletionListenersCount) {
            ExchangeCompletionListener[] old = exchangeCompleteListeners;
            this.exchangeCompleteListeners = exchangeCompleteListeners = new ExchangeCompletionListener[exchangeCompletionListenersCount + 2];
            System.arraycopy(old, 0, exchangeCompleteListeners, 1, exchangeCompletionListenersCount);
            exchangeCompleteListeners[0] = upgradeCompleteListener;
        } else {
            for (int i = exchangeCompletionListenersCount - 1; i >= 0; --i) {
                exchangeCompleteListeners[i + 1] = exchangeCompleteListeners[i];
            }
            exchangeCompleteListeners[0] = upgradeCompleteListener;
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
    public void upgradeChannel(String productName, final ExchangeCompletionListener upgradeCompleteListener) {
        setResponseCode(101);
        final HeaderMap headers = getResponseHeaders();
        headers.put(Headers.UPGRADE, productName);
        headers.put(Headers.CONNECTION, Headers.UPGRADE_STRING);
        final int exchangeCompletionListenersCount = this.exchangeCompletionListenersCount++;
        ExchangeCompletionListener[] exchangeCompleteListeners = this.exchangeCompleteListeners;
        if (exchangeCompleteListeners.length == exchangeCompletionListenersCount) {
            ExchangeCompletionListener[] old = exchangeCompleteListeners;
            this.exchangeCompleteListeners = exchangeCompleteListeners = new ExchangeCompletionListener[exchangeCompletionListenersCount + 2];
            System.arraycopy(old, 0, exchangeCompleteListeners, 1, exchangeCompletionListenersCount);
            exchangeCompleteListeners[0] = upgradeCompleteListener;
        } else {
            for (int i = exchangeCompletionListenersCount - 1; i >= 0; --i) {
                exchangeCompleteListeners[i + 1] = exchangeCompleteListeners[i];
            }
            exchangeCompleteListeners[0] = upgradeCompleteListener;
        }
    }

    public void addExchangeCompleteListener(final ExchangeCompletionListener listener) {
        final int exchangeCompletionListenersCount = this.exchangeCompletionListenersCount++;
        ExchangeCompletionListener[] exchangeCompleteListeners = this.exchangeCompleteListeners;
        if (exchangeCompleteListeners.length == exchangeCompletionListenersCount) {
            ExchangeCompletionListener[] old = exchangeCompleteListeners;
            this.exchangeCompleteListeners = exchangeCompleteListeners = new ExchangeCompletionListener[exchangeCompletionListenersCount + 2];
            System.arraycopy(old, 0, exchangeCompleteListeners, 0, exchangeCompletionListenersCount);
        }
        exchangeCompleteListeners[exchangeCompletionListenersCount] = listener;
    }

    public void addDefaultResponseListener(final DefaultResponseListener listener) {
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
     * @return The content length of the request, or <code>-1</code> if it has not been set
     */
    public long getRequestContentLength() {
        String contentLengthString = requestHeaders.getFirst(Headers.CONTENT_LENGTH);
        if (contentLengthString == null) {
            return -1;
        }
        return Long.parseLong(contentLengthString);
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
     * @return The content length of the response, or <code>-1</code> if it has not been set
     */
    public long getResponseContentLength() {
        String contentLengthString = responseHeaders.getFirst(Headers.CONTENT_LENGTH);
        if (contentLengthString == null) {
            return -1;
        }
        return Long.parseLong(contentLengthString);
    }

    /**
     * Sets the response content length
     *
     * @param length The content length
     */
    public void setResponseContentLength(long length) {
        if(length == -1) {
            responseHeaders.remove(Headers.CONTENT_LENGTH);
        } else {
            responseHeaders.put(Headers.CONTENT_LENGTH, Long.toString(length));
        }
    }

    /**
     * Returns a mutable map of query parameters.
     *
     * @return The query parameters
     */
    public Map<String, Deque<String>> getQueryParameters() {
        if (queryParameters == null) {
            queryParameters = new TreeMap<String, Deque<String>>();
        }
        return queryParameters;
    }

    public void addQueryParam(final String name, final String param) {
        if (queryParameters == null) {
            queryParameters = new TreeMap<String, Deque<String>>();
        }
        Deque<String> list = queryParameters.get(name);
        if (list == null) {
            queryParameters.put(name, list = new ArrayDeque<String>(2));
        }
        list.add(param);
    }


    /**
     * Returns a mutable map of path parameters
     *
     * @return The path parameters
     */
    public Map<String, Deque<String>> getPathParameters() {
        if (pathParameters == null) {
            pathParameters = new TreeMap<String, Deque<String>>();
        }
        return pathParameters;
    }

    public void addPathParam(final String name, final String param) {
        if (pathParameters == null) {
            pathParameters = new TreeMap<String, Deque<String>>();
        }
        Deque<String> list = pathParameters.get(name);
        if (list == null) {
            pathParameters.put(name, list = new ArrayDeque<String>(2));
        }
        list.add(param);
    }

    /**
     *
     * @return A mutable map of request cookies
     */
    public Map<String, Cookie> getRequestCookies() {
        if(requestCookies == null) {
            requestCookies = ExchangeCookieUtils.parseRequestCookies(this);
        }
        return requestCookies;
    }

    /**
     * Sets a response cookie
     * @param cookie The cookie
     */
    public void setResponseCookie(final Cookie cookie) {
        if(responseCookies == null) {
            responseCookies = new TreeMap<String, Cookie>(); //hashmap is slow to allocate in JDK7
        }
        responseCookies.put(cookie.getName(), cookie);
    }

    /**
     * @return A mutable map of response cookies
     */
    public Map<String, Cookie> getResponseCookies() {
        if (responseCookies == null) {
            responseCookies = new TreeMap<String, Cookie>();
        }
        return responseCookies;
    }

    /**
     * For internal use only
     *
     * @return The response cookies, or null if they have not been set yet
     */
    Map<String, Cookie> getResponseCookiesInternal() {
        return responseCookies;
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
        if (requestChannel != null) {
            return null;
        }
        if (anyAreSet(state, FLAG_REQUEST_TERMINATED)) {
            return requestChannel = new ReadDispatchChannel(new EmptyStreamSourceChannel(getIoThread()));
        }
        final ConduitWrapper<StreamSourceConduit>[] wrappers = this.requestWrappers;
        final ConduitStreamSourceChannel sourceChannel = connection.getChannel().getSourceChannel();
        if (wrappers != null) {
            this.requestWrappers = null;
            final WrapperConduitFactory<StreamSourceConduit> factory = new WrapperConduitFactory<StreamSourceConduit>(wrappers, requestWrapperCount, sourceChannel.getConduit(), this);
            sourceChannel.setConduit(factory.create());
        }
        return requestChannel = new ReadDispatchChannel(sourceChannel);
    }

    public boolean isRequestChannelAvailable() {
        return requestChannel == null;
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
        if (requestChannel != null) {
            requestChannel.requestDone();
        }
        this.state = oldVal | FLAG_REQUEST_TERMINATED;
        if (anyAreSet(oldVal, FLAG_RESPONSE_TERMINATED)) {
            invokeExchangeCompleteListeners();
        }
    }

    private void invokeExchangeCompleteListeners() {
        if (exchangeCompletionListenersCount > 0) {
            int i = exchangeCompletionListenersCount - 1;
            ExchangeCompletionListener next = exchangeCompleteListeners[i];
            next.exchangeEvent(this, new ExchangeCompleteNextListener(exchangeCompleteListeners, this, i));
        }
    }

    /**
     * Pushes back the given data. This should only be used by transfer coding handlers that have read past
     * the end of the request when handling pipelined requests
     *
     * @param unget The buffer to push back
     */
    public void ungetRequestBytes(final Pooled<ByteBuffer> unget) {
        if (connection.getExtraBytes() == null) {
            connection.setExtraBytes(unget);
        } else {
            Pooled<ByteBuffer> eb = connection.getExtraBytes();
            ByteBuffer buf = eb.getResource();
            final ByteBuffer ugBuffer = unget.getResource();

            if (ugBuffer.limit() - ugBuffer.remaining() > buf.remaining()) {
                //stuff the existing data after the data we are ungetting
                ugBuffer.compact();
                ugBuffer.put(buf);
                ugBuffer.flip();
                eb.free();
                connection.setExtraBytes(unget);
            } else {
                //TODO: this is horrible, but should not happen often
                final byte[] data = new byte[ugBuffer.remaining() + buf.remaining()];
                int first = ugBuffer.remaining();
                ugBuffer.get(data, 0, ugBuffer.remaining());
                buf.get(data, first, buf.remaining());
                eb.free();
                unget.free();
                final ByteBuffer newBuffer = ByteBuffer.wrap(data);
                connection.setExtraBytes(new Pooled<ByteBuffer>() {
                    @Override
                    public void discard() {

                    }

                    @Override
                    public void free() {

                    }

                    @Override
                    public ByteBuffer getResource() throws IllegalStateException {
                        return newBuffer;
                    }
                });
            }
        }
    }

    /**
     * Get the response channel. The channel must be closed and fully flushed before the next response can be started.
     * In order to close the channel you must first call {@link org.xnio.channels.StreamSinkChannel#shutdownWrites()},
     * and then call {@link org.xnio.channels.StreamSinkChannel#flush()} until it returns true. Alternativly you can
     * call {@link #endExchange()}, which will close the channel as part of its cleanup.
     * <p/>
     * Closing a fixed-length response before the corresponding number of bytes has been written will cause the connection
     * to be reset and subsequent requests to fail; thus it is important to ensure that the proper content length is
     * delivered when one is specified.  The response channel may not be writable until after the response headers have
     * been sent.
     * <p/>
     * If this method is not called then an empty or default response body will be used, depending on the response code set.
     * <p/>
     * The returned channel will begin to write out headers when the first write request is initiated, or when
     * {@link org.xnio.channels.StreamSinkChannel#shutdownWrites()} is called on the channel with no content being written.
     * Once the channel is acquired, however, the response code and headers may not be modified.
     * <p/>
     *
     * @return the response channel, or {@code null} if another party already acquired the channel
     */
    public StreamSinkChannel getResponseChannel() {
        final ConduitWrapper<StreamSinkConduit>[] wrappers = responseWrappers;
        this.responseWrappers = null;
        if (wrappers == null) {
            return null;
        }
        final ConduitStreamSinkChannel sinkChannel = connection.getChannel().getSinkChannel();
        final WrapperConduitFactory<StreamSinkConduit> factory = new WrapperConduitFactory<StreamSinkConduit>(wrappers, responseWrapperCount, sinkChannel.getConduit(), this);
        sinkChannel.setConduit(factory.create());
        this.responseChannel = new WriteDispatchChannel(sinkChannel);
        this.startResponse();
        return responseChannel;
    }

    /**
     * Get the response sender.
     * <p/>
     * For blocking exchanges this will return a sender that uses the underlying output stream.
     *
     * @return the response sender, or {@code null} if another party already acquired the channel or the sender
     * @see #getResponseChannel()
     */
    public Sender getResponseSender() {
        if(sender != null) {
            return sender;
        }

        if (blockingHttpExchange != null) {
            return sender = blockingHttpExchange.getSender();
        }
        return sender = new AsyncSenderImpl(this);
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
     * Adds a {@link ConduitWrapper} to the request wrapper chain.
     *
     * @param wrapper the wrapper
     */
    public void addRequestWrapper(final ConduitWrapper<StreamSourceConduit> wrapper) {
        ConduitWrapper<StreamSourceConduit>[] wrappers = requestWrappers;
        if (requestChannel != null) {
            throw UndertowMessages.MESSAGES.requestChannelAlreadyProvided();
        }
        if (wrappers == null) {
            wrappers = requestWrappers = new ConduitWrapper[2];
        } else if (wrappers.length == requestWrapperCount) {
            requestWrappers = new ConduitWrapper[wrappers.length + 2];
            System.arraycopy(wrappers, 0, requestWrappers, 0, wrappers.length);
            wrappers = requestWrappers;
        }
        wrappers[requestWrapperCount++] = wrapper;
    }

    /**
     * Adds a {@link ConduitWrapper} to the response wrapper chain.
     *
     * @param wrapper the wrapper
     */
    public void addResponseWrapper(final ConduitWrapper<StreamSinkConduit> wrapper) {
        ConduitWrapper<StreamSinkConduit>[] wrappers = responseWrappers;
        if (wrappers == null) {
            throw UndertowMessages.MESSAGES.requestChannelAlreadyProvided();
        }
        if (wrappers.length == responseWrapperCount) {
            responseWrappers = new ConduitWrapper[wrappers.length + 2];
            System.arraycopy(wrappers, 0, responseWrappers, 0, wrappers.length);
            wrappers = responseWrappers;
        }
        wrappers[responseWrapperCount++] = wrapper;
    }

    /**
     * Calling this method puts the exchange in blocking mode, and creates a
     * {@link BlockingHttpExchange} object to store the streams.
     * <p/>
     * When an exchange is in blocking mode the input stream methods become
     * available, other than that there is presently no major difference
     * between blocking an non-blocking modes.
     *
     * @return The existing blocking exchange, if any
     */
    public BlockingHttpExchange startBlocking() {
        final BlockingHttpExchange old = this.blockingHttpExchange;
        blockingHttpExchange = new DefaultBlockingHttpExchange(this);
        return old;
    }

    /**
     * Calling this method puts the exchange in blocking mode, using the given
     * blocking exchange as the source of the streams.
     * <p/>
     * When an exchange is in blocking mode the input stream methods become
     * available, other than that there is presently no major difference
     * between blocking an non-blocking modes.
     * <p/>
     * Note that this method may be called multiple times with different
     * exchange objects, to allow handlers to modify the streams
     * that are being used.
     *
     * @return The existing blocking exchange, if any
     */
    public BlockingHttpExchange startBlocking(final BlockingHttpExchange httpExchange) {
        final BlockingHttpExchange old = this.blockingHttpExchange;
        blockingHttpExchange = httpExchange;
        return old;
    }


    /**
     * @return The input stream
     * @throws IllegalStateException if {@link #startBlocking()} has not been called
     */
    public InputStream getInputStream() {
        if (blockingHttpExchange == null) {
            throw UndertowMessages.MESSAGES.startBlockingHasNotBeenCalled();
        }
        return blockingHttpExchange.getInputStream();
    }

    /**
     * @return The output stream
     * @throws IllegalStateException if {@link #startBlocking()} has not been called
     */
    public OutputStream getOutputStream() {
        if (blockingHttpExchange == null) {
            throw UndertowMessages.MESSAGES.startBlockingHasNotBeenCalled();
        }
        return blockingHttpExchange.getOutputStream();
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
    public void terminateResponse() {
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_TERMINATED)) {
            // idempotent
            return;
        }
        responseChannel.responseDone();
        this.state = oldVal | FLAG_RESPONSE_TERMINATED;
        if (anyAreSet(oldVal, FLAG_REQUEST_TERMINATED)) {
            invokeExchangeCompleteListeners();
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
        final int state = this.state;
        if (allAreSet(state, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED)) {
            return;
        }
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

        if (blockingHttpExchange != null) {
            try {
                //TODO: can we end up in this situation in a IO thread?
                blockingHttpExchange.close();
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(connection.getChannel());
            }
        }

        //417 means that we are rejecting the request
        //so the client should not actually send any data
        //TODO: how
        if (anyAreClear(state, FLAG_REQUEST_TERMINATED)) {

            //not really sure what the best thing to do here is
            //for now we are just going to drain the channel
            if (requestChannel == null) {
                getRequestChannel();
            }
            int totalRead = 0;
            for (; ; ) {
                try {
                    long read = Channels.drain(requestChannel, Long.MAX_VALUE);
                    totalRead += read;
                    if (read == 0) {
                        //if the response code is 417 this is a rejected continuation request.
                        //however there is a chance the client could have sent the data anyway
                        //so we attempt to drain, and if we have not drained anything then we
                        //assume the server has not sent any data

                        if (getResponseCode() != 417 || totalRead > 0) {
                            requestChannel.getReadSetter().set(ChannelListeners.drainListener(Long.MAX_VALUE,
                                    new ChannelListener<StreamSourceChannel>() {
                                        @Override
                                        public void handleEvent(final StreamSourceChannel channel) {
                                            if (anyAreClear(state, FLAG_RESPONSE_TERMINATED)) {
                                                closeAndFlushResponse();
                                            }
                                        }
                                    }, new ChannelExceptionHandler<StreamSourceChannel>() {
                                        @Override
                                        public void handleException(final StreamSourceChannel channel, final IOException e) {
                                            UndertowLogger.REQUEST_LOGGER.debug("Exception draining request stream", e);
                                            IoUtils.safeClose(connection.getChannel());
                                        }
                                    }
                            ));
                            requestChannel.resumeReads();
                            return;
                        } else {
                            break;
                        }
                    } else if (read == -1) {
                        break;
                    }
                } catch (IOException e) {
                    UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                    IoUtils.safeClose(connection.getChannel());
                    break;
                }

            }
        }
        if (anyAreClear(state, FLAG_RESPONSE_TERMINATED)) {
            closeAndFlushResponse();
        }
    }

    private void closeAndFlushResponse() {
        try {
            if (isResponseChannelAvailable()) {
                getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
                getResponseChannel();
            }
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
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
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

        log.tracef("Starting to write response for %s", this);
    }

    public XnioIoThread getIoThread() {
        return connection.getIoThread();
    }

    private static class ExchangeCompleteNextListener implements ExchangeCompletionListener.NextListener {
        private final ExchangeCompletionListener[] list;
        private final HttpServerExchange exchange;
        private int i;

        public ExchangeCompleteNextListener(final ExchangeCompletionListener[] list, final HttpServerExchange exchange, int i) {
            this.list = list;
            this.exchange = exchange;
            this.i = i;
        }

        @Override
        public void proceed() {
            if (--i >= 0) {
                final ExchangeCompletionListener next = list[i];
                next.exchangeEvent(exchange, this);
            }
        }
    }

    private static class DefaultBlockingHttpExchange implements BlockingHttpExchange {

        private InputStream inputStream;
        private OutputStream outputStream;
        private Sender sender;
        private final HttpServerExchange exchange;

        DefaultBlockingHttpExchange(final HttpServerExchange exchange) {
            this.exchange = exchange;
        }

        public InputStream getInputStream() {
            if (inputStream == null) {
                inputStream = new UndertowInputStream(exchange);
            }
            return inputStream;
        }

        public OutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new UndertowOutputStream(exchange);
            }
            return outputStream;
        }

        @Override
        public Sender getSender() {
            if (sender == null) {
                sender = new BlockingSenderImpl(exchange, getOutputStream());
            }
            return sender;
        }

        @Override
        public void close() throws IOException {
            getInputStream().close();
            getOutputStream().close();
        }
    }

    /**
     * Channel implementation that is actually provided to clients of the exchange.
     * <p/>
     * We do not provide the underlying conduit channel, as this is shared between requests, so we need to make sure that after this request
     * is done the the channel cannot affect the next request.
     * <p/>
     * It also delays a wakeup/resumesWrites calls until the current call stack has returned, thus ensuring that only 1 thread is
     * active in the exchange at any one time.
     */
    private class WriteDispatchChannel implements StreamSinkChannel, Runnable {

        protected final StreamSinkChannel delegate;
        protected final ChannelListener.SimpleSetter<WriteDispatchChannel> writeSetter = new ChannelListener.SimpleSetter<WriteDispatchChannel>();
        protected final ChannelListener.SimpleSetter<WriteDispatchChannel> closeSetter = new ChannelListener.SimpleSetter<WriteDispatchChannel>();
        private boolean wakeup;

        public WriteDispatchChannel(final StreamSinkChannel delegate) {
            this.delegate = delegate;
            delegate.getWriteSetter().set(ChannelListeners.delegatingChannelListener(this, writeSetter));
            delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
        }


        @Override
        public void suspendWrites() {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return;
            }
            delegate.suspendWrites();
        }


        @Override
        public boolean isWriteResumed() {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return false;
            }
            return delegate.isWriteResumed();
        }

        @Override
        public void shutdownWrites() throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return;
            }
            delegate.shutdownWrites();
        }

        @Override
        public void awaitWritable() throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            delegate.awaitWritable();
        }

        @Override
        public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            delegate.awaitWritable(time, timeUnit);
        }

        @Override
        public XnioExecutor getWriteThread() {
            return delegate.getWriteThread();
        }

        @Override
        public boolean isOpen() {
            return !allAreSet(state, FLAG_RESPONSE_TERMINATED) && delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) return;
            delegate.close();
        }

        @Override
        public boolean flush() throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return true;
            }
            return delegate.flush();
        }

        @Override
        public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.transferFrom(src, position, count);
        }

        @Override
        public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.transferFrom(source, count, throughBuffer);
        }

        @Override
        public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
            return writeSetter;
        }

        @Override
        public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
            return closeSetter;
        }

        @Override
        public XnioWorker getWorker() {
            return delegate.getWorker();
        }

        @Override
        public XnioIoThread getIoThread() {
            return delegate.getIoThread();
        }

        @Override
        public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.write(srcs, offset, length);
        }

        @Override
        public long write(final ByteBuffer[] srcs) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.write(srcs);
        }

        @Override
        public boolean supportsOption(final Option<?> option) {
            return delegate.supportsOption(option);
        }

        @Override
        public <T> T getOption(final Option<T> option) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.getOption(option);
        }

        @Override
        public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.setOption(option, value);
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.write(src);
        }

        @Override
        public void resumeWrites() {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return;
            }
            if (isInCall()) {
                wakeup = false;
                dispatch(SameThreadExecutor.INSTANCE, this);
            } else {
                delegate.resumeWrites();
            }
        }

        @Override
        public void wakeupWrites() {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return;
            }
            if (isInCall()) {
                wakeup = true;
                dispatch(SameThreadExecutor.INSTANCE, this);
            } else {
                delegate.wakeupWrites();
            }
        }

        @Override
        public void run() {
            if (wakeup) {
                delegate.wakeupWrites();
            } else {
                delegate.resumeWrites();
            }
        }

        public void responseDone() {
            delegate.getCloseSetter().set(null);
            delegate.getWriteSetter().set(null);
            if (delegate.isWriteResumed()) {
                delegate.suspendWrites();
            }
        }
    }

    /**
     * Channel implementation that is actually provided to clients of the exchange. We do not provide the underlying
     * conduit channel, as this will become the next requests conduit channel, so if a thread is still hanging onto this
     * exchange it can result in problems.
     * <p/>
     * It also delays a readResume call until the current call stack has returned, thus ensuring that only 1 thread is
     * active in the exchange at any one time.
     */
    private final class ReadDispatchChannel implements StreamSourceChannel, Runnable {

        private final StreamSourceChannel delegate;

        protected final ChannelListener.SimpleSetter<ReadDispatchChannel> readSetter = new ChannelListener.SimpleSetter<ReadDispatchChannel>();
        protected final ChannelListener.SimpleSetter<ReadDispatchChannel> closeSetter = new ChannelListener.SimpleSetter<ReadDispatchChannel>();

        public ReadDispatchChannel(final StreamSourceChannel delegate) {
            this.delegate = delegate;
            delegate.getReadSetter().set(ChannelListeners.delegatingChannelListener(this, readSetter));
            delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
        }

        @Override
        public void resumeReads() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            if (isInCall()) {
                dispatch(SameThreadExecutor.INSTANCE, this);
            } else {
                delegate.resumeReads();
            }
        }


        @Override
        public void run() {
            if (!allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                delegate.resumeReads();
            }
        }


        public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return -1;
            }
            return delegate.transferTo(position, count, target);
        }

        public void awaitReadable() throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            delegate.awaitReadable();
        }

        public void suspendReads() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            delegate.suspendReads();
        }

        public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return -1;
            }
            return delegate.transferTo(count, throughBuffer, target);
        }

        public XnioWorker getWorker() {
            return delegate.getWorker();
        }

        public boolean isReadResumed() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return false;
            }
            return delegate.isReadResumed();
        }

        public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {

            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.setOption(option, value);
        }

        public boolean supportsOption(final Option<?> option) {
            return delegate.supportsOption(option);
        }

        public void shutdownReads() throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            delegate.shutdownReads();
        }

        public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
            return readSetter;
        }

        public boolean isOpen() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return false;
            }
            return delegate.isOpen();
        }

        public long read(final ByteBuffer[] dsts) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return -1;
            }
            return delegate.read(dsts);
        }

        public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return -1;
            }
            return delegate.read(dsts, offset, length);
        }

        public void wakeupReads() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            delegate.wakeupReads();
        }

        public XnioExecutor getReadThread() {
            return delegate.getReadThread();
        }

        public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            delegate.awaitReadable(time, timeUnit);
        }

        public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
            return closeSetter;
        }

        public void close() throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            delegate.close();
        }

        public <T> T getOption(final Option<T> option) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                throw UndertowMessages.MESSAGES.streamIsClosed();
            }
            return delegate.getOption(option);
        }

        public int read(final ByteBuffer dst) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return -1;
            }
            return delegate.read(dst);
        }

        @Override
        public XnioIoThread getIoThread() {
            return delegate.getIoThread();
        }

        public void requestDone() {
            delegate.getReadSetter().set(null);
            delegate.getCloseSetter().set(null);
            if (delegate.isReadResumed()) {
                delegate.suspendReads();
            }
        }
    }
}
