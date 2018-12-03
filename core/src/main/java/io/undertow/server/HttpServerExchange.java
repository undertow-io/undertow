/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server;

import static io.undertow.util.Bits.allAreSet;
import static io.undertow.util.Bits.anyAreClear;
import static io.undertow.util.Bits.anyAreSet;
import static io.undertow.util.Bits.intBitMask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import org.jboss.logging.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutor;
import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.io.Receiver;
import io.undertow.io.Sender;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Cookies;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.IoUtils;
import io.undertow.util.Methods;
import io.undertow.util.NetworkUtils;
import io.undertow.util.Protocols;
import io.undertow.util.Rfc6265CookieSupport;
import io.undertow.util.StatusCodes;
import io.undertow.util.UndertowOptions;

/**
 * An HTTP server request/response exchange.  An instance of this class is constructed as soon as the request headers are
 * fully parsed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpServerExchange extends AbstractAttachable {

    // immutable state

    private static final Logger log = Logger.getLogger(HttpServerExchange.class);

    private static final RuntimePermission SET_SECURITY_CONTEXT = new RuntimePermission("io.undertow.SET_SECURITY_CONTEXT");
    private static final String ISO_8859_1 = "ISO-8859-1";
    private static final String HTTPS = "https";

    /**
     * The HTTP reason phrase to send. This is an attachment rather than a field as it is rarely used. If this is not set
     * a generic description from the RFC is used instead.
     */
    private static final AttachmentKey<String> REASON_PHRASE = AttachmentKey.create(String.class);

    /**
     * The attachment key that buffered request data is attached under.
     */
    static final AttachmentKey<ByteBuf[]> BUFFERED_REQUEST_DATA = AttachmentKey.create(ByteBuf[].class);

    /**
     * Attachment key that can be used to hold additional request attributes
     */
    public static final AttachmentKey<Map<String, String>> REQUEST_ATTRIBUTES = AttachmentKey.create(Map.class);

    /**
     * Attachment key that can be used as a flag of secure attribute
     */
    public static final AttachmentKey<Boolean> SECURE_REQUEST = AttachmentKey.create(Boolean.class);

    private final ServerConnection connection;
    private final HeaderMap requestHeaders;
    private final HeaderMap responseHeaders;

    private int exchangeCompletionListenersCount = 0;
    private ExchangeCompletionListener[] exchangeCompleteListeners;
    private DefaultResponseListener[] defaultResponseListeners;


    private int responseCommitListenerCount;
    private ResponseCommitListener[] responseCommitListeners;

    private Map<String, Deque<String>> queryParameters;
    private Map<String, Deque<String>> pathParameters;

    private Map<String, Cookie> requestCookies;
    private Map<String, Cookie> responseCookies;

    private BlockingHttpExchange blockingHttpExchange;

    private HttpString protocol;

    /**
     * The security context
     */
    private SecurityContext securityContext;

    // mutable state

    private int state = 200;
    private HttpString requestMethod;
    private String requestScheme;

    /**
     * The original request URI. This will include the host name if it was specified by the client.
     * <p>
     * This is not decoded in any way, and does not include the query string.
     * <p>
     * Examples:
     * GET http://localhost:8080/myFile.jsf?foo=bar HTTP/1.1 -> 'http://localhost:8080/myFile.jsf'
     * POST /my+File.jsf?foo=bar HTTP/1.1 -> '/my+File.jsf'
     */
    private String requestURI;

    /**
     * The request path. This will be decoded by the server, and does not include the query string.
     * <p>
     * This path is not canonicalised, so care must be taken to ensure that escape attacks are not possible.
     * <p>
     * Examples:
     * GET http://localhost:8080/b/../my+File.jsf?foo=bar HTTP/1.1 -> '/b/../my+File.jsf'
     * POST /my+File.jsf?foo=bar HTTP/1.1 -> '/my File.jsf'
     */
    private String requestPath;

    /**
     * The remaining unresolved portion of request path. If a {@link io.undertow.server.handlers.CanonicalPathHandler} is
     * installed this will be canonicalised.
     * <p>
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

    private Sender sender;
    private Receiver receiver;

    private long requestStartTime = -1;


    /**
     * The maximum entity size. This can be modified before the request stream is obtained, however once the request
     * stream is obtained this cannot be modified further.
     * <p>
     * The default value for this is determined by the {@link UndertowOptions#MAX_ENTITY_SIZE} option. A value
     * of 0 indicates that this is unbounded.
     * <p>
     * If this entity size is exceeded the request channel will be forcibly closed.
     * <p>
     * TODO: integrate this with HTTP 100-continue responses, to make it possible to send a 417 rather than just forcibly
     * closing the channel.
     *
     * @see UndertowOptions#MAX_ENTITY_SIZE
     */
    private long maxEntitySize;

    /**
     * When the call stack return this task will be executed by the executor specified in {@link #dispatchExecutor}.
     * If the executor is null then it will be executed by the XNIO worker.
     */
    private Runnable dispatchTask;

    /**
     * The executor that is to be used to dispatch the {@link #dispatchTask}. Note that this is not cleared
     * between dispatches, so once a request has been dispatched once then all subsequent dispatches will use
     * the same executor.
     */
    private Executor dispatchExecutor;

    /**
     * The number of bytes that have been sent to the remote client. This does not include headers,
     * only the entity body, and does not take any transfer or content encoding into account.
     */
    private long responseBytesSent = 0;


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
     * <p>
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
     * <p>
     * This will be true most of the time, this only time this will return
     * false is when performing async operations outside the scope of a call to
     * {@link Connectors#executeRootHandler(HttpHandler, HttpServerExchange)},
     * such as when performing async IO.
     * <p>
     * If this is true then when the call stack returns the exchange will either be dispatched,
     * or the exchange will be ended.
     */
    private static final int FLAG_IN_CALL = 1 << 17;

    /**
     * Flag that indicates the user has started to read data from the request
     */
    private static final int FLAG_REQUEST_READ = 1 << 20;

    /**
     * The source address for the request. If this is null then the actual source address from the channel is used
     */
    private InetSocketAddress sourceAddress;

    /**
     * The destination address for the request. If this is null then the actual source address from the channel is used
     */
    private InetSocketAddress destinationAddress;

    public HttpServerExchange(final ServerConnection connection, long maxEntitySize) {
        this(connection, new HeaderMap(), new HeaderMap(), maxEntitySize);
    }

    public HttpServerExchange(final ServerConnection connection) {
        this(connection, 0);
    }

    public HttpServerExchange(final ServerConnection connection, final HeaderMap requestHeaders, final HeaderMap responseHeaders, long maxEntitySize) {
        this.connection = connection;
        this.maxEntitySize = maxEntitySize;
        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
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
    public HttpServerExchange setProtocol(final HttpString protocol) {
        this.protocol = protocol;
        return this;
    }

    public boolean isSecure() {
        Boolean secure = getAttachment(SECURE_REQUEST);
        if (secure != null && secure) {
            return true;
        }
        String scheme = getRequestScheme();
        if (scheme != null && scheme.equalsIgnoreCase(HTTPS)) {
            return true;
        }
        return false;
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
    public HttpServerExchange setRequestMethod(final HttpString requestMethod) {
        this.requestMethod = requestMethod;
        return this;
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
    public HttpServerExchange setRequestScheme(final String requestScheme) {
        this.requestScheme = requestScheme;
        return this;
    }

    /**
     * The original request URI. This will include the host name, protocol etc
     * if it was specified by the client.
     * <p>
     * This is not decoded in any way, and does not include the query string.
     * <p>
     * Examples:
     * GET http://localhost:8080/myFile.jsf?foo=bar HTTP/1.1 -&gt; 'http://localhost:8080/myFile.jsf'
     * POST /my+File.jsf?foo=bar HTTP/1.1 -&gt; '/my+File.jsf'
     */
    public String getRequestURI() {
        return requestURI;
    }

    /**
     * Sets the request URI
     *
     * @param requestURI The new request URI
     */
    public HttpServerExchange setRequestURI(final String requestURI) {
        this.requestURI = requestURI;
        return this;
    }

    /**
     * Sets the request URI
     *
     * @param requestURI   The new request URI
     * @param containsHost If this is true the request URI contains the host part
     */
    public HttpServerExchange setRequestURI(final String requestURI, boolean containsHost) {
        this.requestURI = requestURI;
        if (containsHost) {
            this.state |= FLAG_URI_CONTAINS_HOST;
        } else {
            this.state &= ~FLAG_URI_CONTAINS_HOST;
        }
        return this;
    }

    /**
     * If a request was submitted to the server with a full URI instead of just a path this
     * will return true. For example:
     * <p>
     * GET http://localhost:8080/b/../my+File.jsf?foo=bar HTTP/1.1 -&gt; true
     * POST /my+File.jsf?foo=bar HTTP/1.1 -&gt; false
     *
     * @return <code>true</code> If the request URI contains the host part of the URI
     */
    public boolean isHostIncludedInRequestURI() {
        return anyAreSet(state, FLAG_URI_CONTAINS_HOST);
    }


    /**
     * The request path. This will be decoded by the server, and does not include the query string.
     * <p>
     * This path is not canonicalised, so care must be taken to ensure that escape attacks are not possible.
     * <p>
     * Examples:
     * GET http://localhost:8080/b/../my+File.jsf?foo=bar HTTP/1.1 -&gt; '/b/../my+File.jsf'
     * POST /my+File.jsf?foo=bar HTTP/1.1 -&gt; '/my File.jsf'
     */
    public String getRequestPath() {
        return requestPath;
    }

    /**
     * Set the request URI path.
     *
     * @param requestPath the request URI path
     */
    public HttpServerExchange setRequestPath(final String requestPath) {
        this.requestPath = requestPath;
        return this;
    }

    /**
     * Get the request relative path.  This is the path which should be evaluated by the current handler.
     * <p>
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
    public HttpServerExchange setRelativePath(final String relativePath) {
        this.relativePath = relativePath;
        return this;
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
    public HttpServerExchange setResolvedPath(final String resolvedPath) {
        this.resolvedPath = resolvedPath;
        return this;
    }

    /**
     * @return The query string, without the leading ?
     */
    public String getQueryString() {
        return queryString;
    }

    public HttpServerExchange setQueryString(final String queryString) {
        this.queryString = queryString;
        return this;
    }

    /**
     * Reconstructs the complete URL as seen by the user. This includes scheme, host name etc,
     * but does not include query string.
     * <p>
     * This is not decoded.
     */
    public String getRequestURL() {
        if (isHostIncludedInRequestURI()) {
            return getRequestURI();
        } else {
            return getRequestScheme() + "://" + getHostAndPort() + getRequestURI();
        }
    }

    /**
     * Returns the request charset. If none was explicitly specified it will return
     * "ISO-8859-1", which is the default charset for HTTP requests.
     *
     * @return The character encoding
     */
    public String getRequestCharset() {
        return extractCharset(requestHeaders);
    }

    /**
     * Returns the response charset. If none was explicitly specified it will return
     * "ISO-8859-1", which is the default charset for HTTP requests.
     *
     * @return The character encoding
     */
    public String getResponseCharset() {
        HeaderMap headers = responseHeaders;
        return extractCharset(headers);
    }

    private String extractCharset(HeaderMap headers) {
        String contentType = headers.getFirst(Headers.CONTENT_TYPE);
        if (contentType != null) {
            String value = Headers.extractQuotedValueFromHeader(contentType, "charset");
            if (value != null) {
                return value;
            }
        }
        return ISO_8859_1;
    }

    /**
     * Return the host that this request was sent to, in general this will be the
     * value of the Host header, minus the port specifier.
     * <p>
     * If this resolves to an IPv6 address it will not be enclosed by square brackets.
     * Care must be taken when constructing URLs based on this method to ensure IPv6 URLs
     * are handled correctly.
     *
     * @return The host part of the destination address
     */
    public String getHostName() {
        String host = requestHeaders.getFirst(Headers.HOST);
        if (host == null) {
            host = getDestinationAddress().getHostString();
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
     * <p>
     * If this resolves to an IPv6 address it *will*  be enclosed by square brackets. The return
     * value of this method is suitable for inclusion in a URL.
     *
     * @return The host and port part of the destination address
     */
    public String getHostAndPort() {
        String host = requestHeaders.getFirst(Headers.HOST);
        if (host == null) {
            InetSocketAddress address = getDestinationAddress();
            host = NetworkUtils.formatPossibleIpv6Address(address.getHostString());
            int port = address.getPort();
            if (!((getRequestScheme().equals("http") && port == 80)
                    || (getRequestScheme().equals("https") && port == 443))) {
                host = host + ":" + port;
            }
        }
        return host;
    }

    /**
     * Return the port that this request was sent to. In general this will be the value of the Host
     * header, minus the host name.
     *
     * @return The port part of the destination address
     */
    public int getHostPort() {
        String host = requestHeaders.getFirst(Headers.HOST);
        if (host != null) {
            //for ipv6 addresses we make sure we take out the first part, which can have multiple occurrences of :
            final int colonIndex;
            if (host.startsWith("[")) {
                colonIndex = host.indexOf(':', host.indexOf(']'));
            } else {
                colonIndex = host.indexOf(':');
            }
            if (colonIndex != -1) {
                try {
                    return Integer.parseInt(host.substring(colonIndex + 1));
                } catch (NumberFormatException ignore) {
                }
            }
            if (getRequestScheme().equals("https")) {
                return 443;
            } else if (getRequestScheme().equals("http")) {
                return 80;
            }

        }
        return getDestinationAddress().getPort();
    }

    /**
     * Get the underlying HTTP connection.
     *
     * @return the underlying HTTP connection
     */
    public ServerConnection getConnection() {
        return connection;
    }

    public boolean isPersistent() {
        return anyAreSet(state, FLAG_PERSISTENT);
    }

    /**
     * @return <code>true</code> If the current thread in the IO thread for the exchange
     */
    public boolean isInIoThread() {
        return getIoThread().inEventLoop();
    }

    /**
     * @return True if this exchange represents an upgrade response
     */
    public boolean isUpgrade() {
        return getStatusCode() == StatusCodes.SWITCHING_PROTOCOLS;
    }

    /**
     * @return The number of bytes sent in the entity body
     */
    public long getResponseBytesSent() {
        if (Connectors.isEntityBodyAllowed(this) && !getRequestMethod().equals(Methods.HEAD)) {
            return responseBytesSent;
        } else {
            return 0; //body is not allowed, even if we attempt to write it will be ignored
        }
    }

    /**
     * Updates the number of response bytes sent. Used when compression is in use
     *
     * @param bytes The number of bytes to increase the response size by. May be negative
     */
    void updateBytesSent(long bytes) {
        if (Connectors.isEntityBodyAllowed(this) && !getRequestMethod().equals(Methods.HEAD)) {
            responseBytesSent += bytes;
        }
    }

    public HttpServerExchange setPersistent(final boolean persistent) {
        if (persistent) {
            this.state = this.state | FLAG_PERSISTENT;
        } else {
            this.state = this.state & ~FLAG_PERSISTENT;
        }
        return this;
    }

    public boolean isDispatched() {
        return anyAreSet(state, FLAG_DISPATCHED);
    }

    public HttpServerExchange unDispatch() {
        state &= ~FLAG_DISPATCHED;
        dispatchTask = null;
        return this;
    }

    /**
     * {@link #dispatch(Executor, Runnable)} should be used instead of this method, as it is hard to use safely.
     * <p>
     * Use {@link io.undertow.util.SameThreadExecutor#INSTANCE} if you do not want to dispatch to another thread.
     *
     * @return this exchange
     */
    @Deprecated
    public HttpServerExchange dispatch() {
        state |= FLAG_DISPATCHED;
        return this;
    }

    /**
     * Dispatches this request to the XNIO worker thread pool. Once the call stack returns
     * the given runnable will be submitted to the executor.
     * <p>
     * In general handlers should first check the value of {@link #isInIoThread()} before
     * calling this method, and only dispatch if the request is actually running in the IO
     * thread.
     *
     * @param runnable The task to run
     * @throws IllegalStateException If this exchange has already been dispatched
     */
    public HttpServerExchange dispatch(final Runnable runnable) {
        dispatch(null, runnable);
        return this;
    }

    /**
     * Dispatches this request to the given executor. Once the call stack returns
     * the given runnable will be submitted to the executor.
     * <p>
     * In general handlers should first check the value of {@link #isInIoThread()} before
     * calling this method, and only dispatch if the request is actually running in the IO
     * thread.
     *
     * @param runnable The task to run
     * @throws IllegalStateException If this exchange has already been dispatched
     */
    public HttpServerExchange dispatch(final Executor executor, final Runnable runnable) {
        if (isInCall()) {
            if (executor != null) {
                this.dispatchExecutor = executor;
            }
            state |= FLAG_DISPATCHED;
            if (connection.isIoOperationQueued()) {
                throw UndertowMessages.MESSAGES.resumedAndDispatched();
            }
            this.dispatchTask = runnable;
        } else {
            if (executor == null) {
                getConnection().getWorker().execute(runnable);
            } else {
                executor.execute(runnable);
            }
        }
        return this;
    }

    public HttpServerExchange dispatch(final HttpHandler handler) {
        dispatch(null, handler);
        return this;
    }

    public HttpServerExchange dispatch(final Executor executor, final HttpHandler handler) {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Connectors.executeRootHandler(handler, HttpServerExchange.this);
            }
        };
        dispatch(executor, runnable);
        return this;
    }

    /**
     * Sets the executor that is used for dispatch operations where no executor is specified.
     *
     * @param executor The executor to use
     */
    public HttpServerExchange setDispatchExecutor(final Executor executor) {
        if (executor == null) {
            dispatchExecutor = null;
        } else {
            dispatchExecutor = executor;
        }
        return this;
    }

    /**
     * Gets the current executor that is used for dispatch operations. This may be null
     *
     * @return The current dispatch executor
     */
    public Executor getDispatchExecutor() {
        return dispatchExecutor;
    }

    /**
     * @return The current dispatch task
     */
    Runnable getDispatchTask() {
        return dispatchTask;
    }

    boolean isInCall() {
        return anyAreSet(state, FLAG_IN_CALL);
    }

    HttpServerExchange setInCall(boolean value) {
        if (value) {
            state |= FLAG_IN_CALL;
        } else {
            state &= ~FLAG_IN_CALL;
        }
        return this;
    }


//    /**
//     * Upgrade the channel to a raw socket. This method set the response code to 101, and then marks both the
//     * request and response as terminated, which means that once the current request is completed the raw channel
//     * can be obtained from {@link io.undertow.server.protocol.http.HttpServerConnection#getChannel()}
//     *
//     * @throws IllegalStateException if a response or upgrade was already sent, or if the request body is already being
//     *                               read
//     */
//    public HttpServerExchange upgradeChannel(final HttpUpgradeListener listener) {
//        if (!connection.isUpgradeSupported()) {
//            throw UndertowMessages.MESSAGES.upgradeNotSupported();
//        }
//        if(!getRequestHeaders().contains(Headers.UPGRADE)) {
//            throw UndertowMessages.MESSAGES.notAnUpgradeRequest();
//        }
//        UndertowLogger.REQUEST_LOGGER.debugf("Upgrading request %s", this);
//        connection.setUpgradeListener(listener);
//        setStatusCode(StatusCodes.SWITCHING_PROTOCOLS);
//        getResponseHeaders().put(Headers.CONNECTION, Headers.UPGRADE_STRING);
//        return this;
//    }
//
//    /**
//     * Upgrade the channel to a raw socket. This method set the response code to 101, and then marks both the
//     * request and response as terminated, which means that once the current request is completed the raw channel
//     * can be obtained from {@link io.undertow.server.protocol.http.HttpServerConnection#getChannel()}
//     *
//     * @param productName the product name to report to the client
//     * @throws IllegalStateException if a response or upgrade was already sent, or if the request body is already being
//     *                               read
//     */
//    public HttpServerExchange upgradeChannel(String productName, final HttpUpgradeListener listener) {
//        if (!connection.isUpgradeSupported()) {
//            throw UndertowMessages.MESSAGES.upgradeNotSupported();
//        }
//        UndertowLogger.REQUEST_LOGGER.debugf("Upgrading request %s", this);
//        connection.setUpgradeListener(listener);
//        setStatusCode(StatusCodes.SWITCHING_PROTOCOLS);
//        final HeaderMap headers = getResponseHeaders();
//        headers.put(Headers.UPGRADE, productName);
//        headers.put(Headers.CONNECTION, Headers.UPGRADE_STRING);
//        return this;
//    }
//
//    /**
//     *
//     * @param connectListener
//     * @return
//     */
//    public HttpServerExchange acceptConnectRequest(HttpUpgradeListener connectListener) {
//        if(!getRequestMethod().equals(Methods.CONNECT)) {
//            throw UndertowMessages.MESSAGES.notAConnectRequest();
//        }
//        connection.setConnectListener(connectListener);
//        return this;
//    }


    public HttpServerExchange addExchangeCompleteListener(final ExchangeCompletionListener listener) {
        if (isComplete() || this.exchangeCompletionListenersCount == -1) {
            throw UndertowMessages.MESSAGES.exchangeAlreadyComplete();
        }
        final int exchangeCompletionListenersCount = this.exchangeCompletionListenersCount++;
        ExchangeCompletionListener[] exchangeCompleteListeners = this.exchangeCompleteListeners;
        if (exchangeCompleteListeners == null || exchangeCompleteListeners.length == exchangeCompletionListenersCount) {
            ExchangeCompletionListener[] old = exchangeCompleteListeners;
            this.exchangeCompleteListeners = exchangeCompleteListeners = new ExchangeCompletionListener[exchangeCompletionListenersCount + 2];
            if (old != null) {
                System.arraycopy(old, 0, exchangeCompleteListeners, 0, exchangeCompletionListenersCount);
            }
        }
        exchangeCompleteListeners[exchangeCompletionListenersCount] = listener;
        return this;
    }

    public HttpServerExchange addDefaultResponseListener(final DefaultResponseListener listener) {
        int i = 0;
        if (defaultResponseListeners == null) {
            defaultResponseListeners = new DefaultResponseListener[2];
        } else {
            while (i != defaultResponseListeners.length && defaultResponseListeners[i] != null) {
                ++i;
            }
            if (i == defaultResponseListeners.length) {
                DefaultResponseListener[] old = defaultResponseListeners;
                defaultResponseListeners = new DefaultResponseListener[defaultResponseListeners.length + 2];
                System.arraycopy(old, 0, defaultResponseListeners, 0, old.length);
            }
        }
        defaultResponseListeners[i] = listener;
        return this;
    }

    /**
     * Get the source address of the HTTP request.
     *
     * @return the source address of the HTTP request
     */
    public InetSocketAddress getSourceAddress() {
        if (sourceAddress != null) {
            return sourceAddress;
        }
        return connection.getPeerAddress(InetSocketAddress.class);
    }

    /**
     * Sets the source address of the HTTP request. If this is not explicitly set
     * the actual source address of the channel is used.
     *
     * @param sourceAddress The address
     */
    public HttpServerExchange setSourceAddress(InetSocketAddress sourceAddress) {
        this.sourceAddress = sourceAddress;
        return this;
    }

    /**
     * Get the destination address of the HTTP request.
     *
     * @return the destination address of the HTTP request
     */
    public InetSocketAddress getDestinationAddress() {
        if (destinationAddress != null) {
            return destinationAddress;
        }
        return connection.getLocalAddress(InetSocketAddress.class);
    }

    /**
     * Sets the destination address of the HTTP request. If this is not explicitly set
     * the actual destination address of the channel is used.
     *
     * @param destinationAddress The address
     */
    public HttpServerExchange setDestinationAddress(InetSocketAddress destinationAddress) {
        this.destinationAddress = destinationAddress;
        return this;
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
    public HttpServerExchange setResponseContentLength(long length) {
        if (length == -1) {
            responseHeaders.remove(Headers.CONTENT_LENGTH);
        } else {
            responseHeaders.put(Headers.CONTENT_LENGTH, Long.toString(length));
        }
        return this;
    }

    /**
     * Returns a mutable map of query parameters.
     *
     * @return The query parameters
     */
    public Map<String, Deque<String>> getQueryParameters() {
        if (queryParameters == null) {
            queryParameters = new TreeMap<>();
        }
        return queryParameters;
    }

    public HttpServerExchange addQueryParam(final String name, final String param) {
        if (queryParameters == null) {
            queryParameters = new TreeMap<>();
        }
        Deque<String> list = queryParameters.get(name);
        if (list == null) {
            queryParameters.put(name, list = new ArrayDeque<>(2));
        }
        list.add(param);
        return this;
    }


    /**
     * Returns a mutable map of path parameters
     *
     * @return The path parameters
     */
    public Map<String, Deque<String>> getPathParameters() {
        if (pathParameters == null) {
            pathParameters = new TreeMap<>();
        }
        return pathParameters;
    }

    public HttpServerExchange addPathParam(final String name, final String param) {
        if (pathParameters == null) {
            pathParameters = new TreeMap<>();
        }
        Deque<String> list = pathParameters.get(name);
        if (list == null) {
            pathParameters.put(name, list = new ArrayDeque<>(2));
        }
        list.add(param);
        return this;
    }

    /**
     * @return A mutable map of request cookies
     */
    public Map<String, Cookie> getRequestCookies() {
        if (requestCookies == null) {
            requestCookies = Cookies.parseRequestCookies(
                    getConnection().getUndertowOptions().get(UndertowOptions.MAX_COOKIES, 200),
                    getConnection().getUndertowOptions().get(UndertowOptions.ALLOW_EQUALS_IN_COOKIE_VALUE, false),
                    requestHeaders.get(Headers.COOKIE));
        }
        return requestCookies;
    }

    /**
     * Sets a response cookie
     *
     * @param cookie The cookie
     */
    public HttpServerExchange setResponseCookie(final Cookie cookie) {
        if (getConnection().getUndertowOptions().get(UndertowOptions.ENABLE_RFC6265_COOKIE_VALIDATION, UndertowOptions.DEFAULT_ENABLE_RFC6265_COOKIE_VALIDATION)) {
            if (cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                Rfc6265CookieSupport.validateCookieValue(cookie.getValue());
            }
            if (cookie.getPath() != null && !cookie.getPath().isEmpty()) {
                Rfc6265CookieSupport.validatePath(cookie.getPath());
            }
            if (cookie.getDomain() != null && !cookie.getDomain().isEmpty()) {
                Rfc6265CookieSupport.validateDomain(cookie.getDomain());
            }
        }
        if (responseCookies == null) {
            responseCookies = new TreeMap<>(); //hashmap is slow to allocate in JDK7
        }
        responseCookies.put(cookie.getName(), cookie);
        return this;
    }

    /**
     * @return A mutable map of response cookies
     */
    public Map<String, Cookie> getResponseCookies() {
        if (responseCookies == null) {
            responseCookies = new TreeMap<>();
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
     * Reads some data. If all data has been read it will return null.
     *
     * @return
     * @throws IOException on failure
     */
    public ByteBuf readBlocking() throws IOException {
        if (anyAreSet(state, FLAG_REQUEST_TERMINATED)) {
            return null;
        }
        return connection.readBlocking();
    }

    <T> void writeAsync(ByteBuf data, boolean last, IoCallback<T> callback, T context) {
        if (data == null && !last) {
            throw new IllegalArgumentException("cannot call write with a null buffer and last being false");
        }
        if (anyAreSet(state, FLAG_RESPONSE_TERMINATED)) {
            callback.onException(this, context, new IOException(UndertowMessages.MESSAGES.responseComplete()));
            return;
        }
        handleFirstData();
        connection.writeAsync(data, last, this, callback, context);
    }

    void writeBlocking(ByteBuf data, boolean last) throws IOException {
        if (data == null && !last) {
            throw new IllegalArgumentException("cannot call write with a null buffer and last being false");
        }
        if (anyAreSet(state, FLAG_RESPONSE_TERMINATED)) {
            throw UndertowMessages.MESSAGES.responseComplete();
        }
        handleFirstData();
        connection.writeBlocking(data, last, this);
    }

    private void handleFirstData() {
        if (anyAreClear(state, FLAG_RESPONSE_SENT)) {
            state |= FLAG_RESPONSE_SENT;
            for (int i = responseCommitListenerCount - 1; i >= 0; --i) {
                responseCommitListeners[i].beforeCommit(this);
            }
            Connectors.flattenCookies(this);
        }
    }

    ChannelFuture writeFileAsync(FileChannel file, long position, long count) {
        if (anyAreSet(state, FLAG_RESPONSE_TERMINATED)) {
            ChannelPromise promise = connection.createPromise();
            promise.setFailure(UndertowMessages.MESSAGES.responseComplete());
            return promise;
        }
        handleFirstData();
        return connection.writeFileAsync(file, position, count, this);
    }

    void writeFileBlocking(FileChannel file, long position, long count) throws IOException {
        if (anyAreSet(state, FLAG_RESPONSE_TERMINATED)) {
            throw UndertowMessages.MESSAGES.responseComplete();
        }
        handleFirstData();
        connection.writeFileBlocking(file, position, count, this);
    }

    /**
     * Returns true if the completion handler for this exchange has been invoked, and the request is considered
     * finished.
     */
    public boolean isComplete() {
        return allAreSet(state, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED);
    }

    /**
     * Returns true if all data has been read from the request, or if there
     * was not data.
     *
     * @return true if the request is complete
     */
    public boolean isRequestComplete() {
        ByteBuf[] data = getAttachment(BUFFERED_REQUEST_DATA);
        if (data != null) {
            return false;
        }
        return allAreSet(state, FLAG_REQUEST_TERMINATED);
    }

    /**
     * @return true if the responses is complete
     */
    public boolean isResponseComplete() {
        return allAreSet(state, FLAG_RESPONSE_TERMINATED);
    }

    /**
     * Force the codec to treat the request as fully read.  Should only be invoked by handlers which downgrade
     * the socket or implement a transfer coding.
     */
    void terminateRequest() {
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_REQUEST_TERMINATED)) {
            // idempotent
            return;
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
            exchangeCompletionListenersCount = -1;
            next.exchangeEvent(this, new ExchangeCompleteNextListener(exchangeCompleteListeners, this, i));
        } else if (exchangeCompletionListenersCount == 0) {
            exchangeCompletionListenersCount = -1;
            connection.exchangeComplete(this);
        }
    }


    /**
     * Get the response sender.
     * <p>
     * For blocking exchanges this will return a sender that uses the underlying output stream.
     *
     * @return the response sender, or {@code null} if another party already acquired the channel or the sender
     * @see #getResponseChannel()
     */
    public Sender getResponseSender() {
        if (blockingHttpExchange != null) {
            return blockingHttpExchange.getSender();
        }
        if (sender != null) {
            return sender;
        }
        return sender = new AsyncSenderImpl(this);
    }

    public Receiver getRequestReceiver() {
        if (blockingHttpExchange != null) {
            return blockingHttpExchange.getReceiver();
        }
        if (receiver != null) {
            return receiver;
        }
        throw new RuntimeException("NYI");
        //return receiver = new AsyncReceiverImpl(this);
    }

    /**
     * Get the status code.
     *
     * @return the status code
     * @see #getStatusCode()
     */
    @Deprecated
    public int getResponseCode() {
        return state & MASK_RESPONSE_CODE;
    }

    /**
     * Change the status code for this response.  If not specified, the code will be a {@code 200}.  Setting
     * the status code after the response headers have been transmitted has no effect.
     *
     * @param statusCode the new code
     * @throws IllegalStateException if a response or upgrade was already sent
     * @see #setStatusCode(int)
     */
    @Deprecated
    public HttpServerExchange setResponseCode(final int statusCode) {
        return setStatusCode(statusCode);
    }

    /**
     * Get the status code.
     *
     * @return the status code
     */
    public int getStatusCode() {
        return state & MASK_RESPONSE_CODE;
    }

    /**
     * Change the status code for this response.  If not specified, the code will be a {@code 200}.  Setting
     * the status code after the response headers have been transmitted has no effect.
     *
     * @param statusCode the new code
     * @throws IllegalStateException if a response or upgrade was already sent
     */
    public HttpServerExchange setStatusCode(final int statusCode) {
        if (statusCode < 0 || statusCode > 999) {
            throw new IllegalArgumentException("Invalid response code");
        }
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
            throw UndertowMessages.MESSAGES.responseAlreadyStarted();
        }
        if (statusCode >= 500) {
            if (UndertowLogger.ERROR_RESPONSE.isDebugEnabled()) {
                UndertowLogger.ERROR_RESPONSE.debugf(new RuntimeException(), "Setting error code %s for exchange %s", statusCode, this);
            }
        }
        this.state = oldVal & ~MASK_RESPONSE_CODE | statusCode & MASK_RESPONSE_CODE;
        return this;
    }

    /**
     * Sets the HTTP reason phrase. Depending on the protocol this may or may not be honoured. In particular HTTP2
     * has removed support for the reason phrase.
     * <p>
     * This method should only be used to interact with legacy frameworks that give special meaning to the reason phrase.
     *
     * @param message The status message
     * @return this exchange
     */
    public HttpServerExchange setReasonPhrase(String message) {
        putAttachment(REASON_PHRASE, message);
        return this;
    }

    /**
     * @return The current reason phrase
     */
    public String getReasonPhrase() {
        return getAttachment(REASON_PHRASE);
    }


    /**
     * Calling this method puts the exchange in blocking mode, and creates a
     * {@link BlockingHttpExchange} object to store the streams.
     * <p>
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
     * <p>
     * When an exchange is in blocking mode the input stream methods become
     * available, other than that there is presently no major difference
     * between blocking an non-blocking modes.
     * <p>
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
     * Returns true if {@link #startBlocking()} or {@link #startBlocking(BlockingHttpExchange)} has been called.
     *
     * @return <code>true</code> If this is a blocking HTTP server exchange
     */
    public boolean isBlocking() {
        return blockingHttpExchange != null;
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
     * Force the codec to treat the response as fully written.  Should only be invoked by handlers which downgrade
     * the socket or implement a transfer coding.
     */
    HttpServerExchange terminateResponse() {
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_TERMINATED)) {
            // idempotent
            return this;
        }
        this.state = oldVal | FLAG_RESPONSE_TERMINATED;
        if (anyAreSet(oldVal, FLAG_REQUEST_TERMINATED)) {
            invokeExchangeCompleteListeners();
        }
        return this;
    }

    /**
     * @return The request start time, or -1 if this was not recorded
     */
    public long getRequestStartTime() {
        return requestStartTime;
    }


    HttpServerExchange setRequestStartTime(long requestStartTime) {
        this.requestStartTime = requestStartTime;
        return this;
    }

    /**
     * Ends the exchange by fully draining the request channel, and flushing the response channel.
     * <p>
     * This can result in handoff to an XNIO worker, so after this method is called the exchange should
     * not be modified by the caller.
     * <p>
     * If the exchange is already complete this method is a noop
     */
    public HttpServerExchange endExchange() {
        final int state = this.state;
        if (allAreSet(state, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED)) {
            if (blockingHttpExchange != null) {
                //we still have to close the blocking exchange in this case,
                IoUtils.safeClose(blockingHttpExchange);
            }
            return this;
        }
        if (defaultResponseListeners != null) {
            int i = defaultResponseListeners.length - 1;
            while (i >= 0) {
                DefaultResponseListener listener = defaultResponseListeners[i];
                if (listener != null) {
                    defaultResponseListeners[i] = null;
                    try {
                        if (listener.handleDefaultResponse(this)) {
                            return this;
                        }
                    } catch (Throwable e) {
                        UndertowLogger.REQUEST_LOGGER.debug("Exception running default response listener", e);
                    }
                }
                i--;
            }
        }

        if (blockingHttpExchange != null) {
            try {
                //TODO: can we end up in this situation in a IO thread?
                //this will end the exchange in a blocking manner
                blockingHttpExchange.close();
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(connection);
            } catch (Throwable t) {
                UndertowLogger.REQUEST_IO_LOGGER.handleUnexpectedFailure(t);
                IoUtils.safeClose(connection);
            }
        }
        if (!isRequestComplete()) {
            connection.setReadCallback(new IoCallback<Object>() {
                @Override
                public void onComplete(HttpServerExchange exchange, Object context) {
                    try {
                        boolean done = false;
                        while (connection.isReadDataAvailable()) {
                            ByteBuf res = exchange.readAsync();
                            if (res != null) {
                                res.release();
                            } else {
                                done = true;
                            }
                        }
                        if(!done) {
                            connection.setReadCallback(this, null);
                        }
                    } catch (IOException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                    }
                }

                @Override
                public void onException(HttpServerExchange exchange, Object context, IOException exception) {
                    UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                }
            }, null);
        }

        if (!isResponseComplete()) {
            writeAsync(null, true, IoCallback.END_EXCHANGE, null);
        }
        return this;
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
    HttpServerExchange startResponse() throws IllegalStateException {
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
            throw UndertowMessages.MESSAGES.responseAlreadyStarted();
        }
        this.state = oldVal | FLAG_RESPONSE_SENT;

        log.tracef("Starting to write response for %s", this);
        return this;
    }

    public EventExecutor getIoThread() {
        return connection.getIoThread();
    }

    /**
     * @return The maximum entity size for this exchange
     */
    public long getMaxEntitySize() {
        return maxEntitySize;
    }

    /**
     * Sets the max entity size for this exchange. This cannot be modified after the request channel has been obtained.
     *
     * @param maxEntitySize The max entity size
     */
    public HttpServerExchange setMaxEntitySize(final long maxEntitySize) {
        if (anyAreSet(state, FLAG_REQUEST_READ)) {
            throw UndertowMessages.MESSAGES.requestChannelAlreadyProvided();
        }
        this.maxEntitySize = maxEntitySize;
        connection.maxEntitySizeUpdated(this);
        return this;
    }

    public SecurityContext getSecurityContext() {
        return securityContext;
    }

    public void setSecurityContext(SecurityContext securityContext) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_SECURITY_CONTEXT);
        }
        this.securityContext = securityContext;
    }

    /**
     * Adds a listener that will be invoked on response commit
     *
     * @param listener The response listener
     */
    public void addResponseCommitListener(final ResponseCommitListener listener) {
        if (isComplete() || this.responseCommitListenerCount == -1) {
            throw UndertowMessages.MESSAGES.responseAlreadyStarted();
        }
        final int responseCommitListenerCount = this.responseCommitListenerCount++;
        ResponseCommitListener[] responseCommitListeners = this.responseCommitListeners;
        if (responseCommitListeners == null || responseCommitListeners.length == responseCommitListenerCount) {
            ResponseCommitListener[] old = responseCommitListeners;
            this.responseCommitListeners = responseCommitListeners = new ResponseCommitListener[responseCommitListenerCount + 2];
            if (old != null) {
                System.arraycopy(old, 0, responseCommitListeners, 0, responseCommitListenerCount);
            }
        }
        responseCommitListeners[responseCommitListenerCount] = listener;
    }

    boolean isReadDataAvailable() {
        return connection.isReadDataAvailable();
    }

    /**
     * Reads some data from the exchange. Can only be called if {@link #isReadDataAvailable()} returns true.
     * <p>
     * Returns null when all data is full read
     *
     * @return
     * @throws IOException
     */
    ByteBuf readAsync() throws IOException {
        return connection.readAsync();
    }


    private static class ExchangeCompleteNextListener implements ExchangeCompletionListener.NextListener {
        private final ExchangeCompletionListener[] list;
        private final HttpServerExchange exchange;
        private int i;

        ExchangeCompleteNextListener(final ExchangeCompletionListener[] list, final HttpServerExchange exchange, int i) {
            this.list = list;
            this.exchange = exchange;
            this.i = i;
        }

        @Override
        public void proceed() {
            if (--i >= 0) {
                final ExchangeCompletionListener next = list[i];
                next.exchangeEvent(exchange, this);
            } else if (i == -1) {
                exchange.connection.exchangeComplete(exchange);
            }
        }
    }

    private static class DefaultBlockingHttpExchange implements BlockingHttpExchange {

        private InputStream inputStream;
        private UndertowOutputStream outputStream;
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

        public UndertowOutputStream getOutputStream() {
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
            try {
                getInputStream().close();
            } finally {
                getOutputStream().close();
            }
        }

        @Override
        public Receiver getReceiver() {
            //return new BlockingReceiverImpl(exchange, getInputStream());
            throw new RuntimeException("NYI");
        }
    }

    @Override
    public String toString() {
        return "HttpServerExchange{ " + getRequestMethod().toString() + " " + getRequestURI() + " request " + requestHeaders + " response " + responseHeaders + '}';
    }
}
