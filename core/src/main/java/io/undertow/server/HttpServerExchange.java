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

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.channels.DetachableStreamSinkChannel;
import io.undertow.channels.DetachableStreamSourceChannel;
import io.undertow.conduits.EmptyStreamSourceConduit;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.io.AsyncReceiverImpl;
import io.undertow.io.AsyncSenderImpl;
import io.undertow.io.BlockingReceiverImpl;
import io.undertow.io.BlockingSenderImpl;
import io.undertow.io.Receiver;
import io.undertow.io.Sender;
import io.undertow.io.UndertowInputStream;
import io.undertow.io.UndertowOutputStream;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Cookies;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.NetworkUtils;
import io.undertow.util.Protocols;
import io.undertow.util.Rfc6265CookieSupport;
import io.undertow.util.StatusCodes;
import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.XnioIoThread;
import org.xnio.channels.Channels;
import org.xnio.channels.Configurable;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.Conduit;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.intBitMask;

/**
 * An HTTP server request/response exchange.  An instance of this class is constructed as soon as the request headers are
 * fully parsed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
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
    static final AttachmentKey<PooledByteBuffer[]> BUFFERED_REQUEST_DATA = AttachmentKey.create(PooledByteBuffer[].class);

    /**
     * Attachment key that can be used to hold additional request attributes
     */
    public static final AttachmentKey<Map<String, String>> REQUEST_ATTRIBUTES = AttachmentKey.create(Map.class);

    /**
     * Attachment key that can be used to hold a remotely authenticated user
     */
    public static final AttachmentKey<String> REMOTE_USER = AttachmentKey.create(String.class);


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

    private Map<String, Deque<String>> queryParameters;
    private Map<String, Deque<String>> pathParameters;

    private DelegatingIterable<Cookie> requestCookies;
    private DelegatingIterable<Cookie> responseCookies;

    private Map<String, Cookie> deprecatedRequestCookies;
    private Map<String, Cookie> deprecatedResponseCookies;

    /**
     * The actual response channel. May be null if it has not been created yet.
     */
    private WriteDispatchChannel responseChannel;
    /**
     * The actual request channel. May be null if it has not been created yet.
     */
    protected ReadDispatchChannel requestChannel;

    private BlockingHttpExchange blockingHttpExchange;

    private HttpString protocol;

    /**
     * The security context
     */
    private SecurityContext securityContext;

    // mutable state

    private int state = 200;
    private HttpString requestMethod = HttpString.EMPTY;
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

    private int requestWrapperCount = 0;
    private ConduitWrapper<StreamSourceConduit>[] requestWrappers; //we don't allocate these by default, as for get requests they are not used

    private int responseWrapperCount = 0;
    private ConduitWrapper<StreamSinkConduit>[] responseWrappers;

    private Sender sender;
    private Receiver receiver;

    private long requestStartTime = -1;


    /**
     * The maximum entity size. This can be modified before the request stream is obtained, however once the request
     * stream is obtained this cannot be modified further.
     * <p>
     * The default value for this is determined by the {@link io.undertow.UndertowOptions#MAX_ENTITY_SIZE} option. A value
     * of 0 indicates that this is unbounded.
     * <p>
     * If this entity size is exceeded the request channel will be forcibly closed.
     * <p>
     * TODO: integrate this with HTTP 100-continue responses, to make it possible to send a 417 rather than just forcibly
     * closing the channel.
     *
     * @see io.undertow.UndertowOptions#MAX_ENTITY_SIZE
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
     * Flag that indicates that reads should be resumed when the call stack returns.
     */
    private static final int FLAG_SHOULD_RESUME_READS = 1 << 18;

    /**
     * Flag that indicates that writes should be resumed when the call stack returns
     */
    private static final int FLAG_SHOULD_RESUME_WRITES = 1 << 19;

    /**
     * Flag that indicates that the request channel has been reset, and {@link #getRequestChannel()} can be called again
     */
    private static final int FLAG_REQUEST_RESET= 1 << 20;

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

    public HttpServerExchange(final ServerConnection connection, final HeaderMap requestHeaders, final HeaderMap responseHeaders,  long maxEntitySize) {
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

    public boolean isSecure() {
        Boolean secure = getAttachment(SECURE_REQUEST);
        if(secure != null && secure) {
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
     *
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
        if (host == null || "".equals(host.trim())) {
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
        if (host == null || "".equals(host.trim())) {
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
                } catch (NumberFormatException ignore) {}
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
     *
     * @return <code>true</code> If the current thread in the IO thread for the exchange
     */
    public boolean isInIoThread() {
        return getIoThread() == Thread.currentThread();
    }

    /**
     *
     * @return True if this exchange represents an upgrade response
     */
    public boolean isUpgrade() {
        return getStatusCode() == StatusCodes.SWITCHING_PROTOCOLS;
    }

    /**
     *
     * @return The number of bytes sent in the entity body
     */
    public long getResponseBytesSent() {
        if(Connectors.isEntityBodyAllowed(this) && !getRequestMethod().equals(Methods.HEAD)) {
            return responseBytesSent;
        } else {
            return 0; //body is not allowed, even if we attempt to write it will be ignored
        }
    }

    /**
     * Updates the number of response bytes sent. Used when compression is in use
     * @param bytes The number of bytes to increase the response size by. May be negative
     */
    void updateBytesSent(long bytes) {
        if(Connectors.isEntityBodyAllowed(this) && !getRequestMethod().equals(Methods.HEAD)) {
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
     *
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
            if(anyAreSet(state, FLAG_SHOULD_RESUME_READS | FLAG_SHOULD_RESUME_WRITES)) {
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


    /**
     * Upgrade the channel to a raw socket. This method set the response code to 101, and then marks both the
     * request and response as terminated, which means that once the current request is completed the raw channel
     * can be obtained from {@link io.undertow.server.protocol.http.HttpServerConnection#getChannel()}
     *
     * @throws IllegalStateException if a response or upgrade was already sent, or if the request body is already being
     *                               read
     */
    public HttpServerExchange upgradeChannel(final HttpUpgradeListener listener) {
        if (!connection.isUpgradeSupported()) {
            throw UndertowMessages.MESSAGES.upgradeNotSupported();
        }
        if(!getRequestHeaders().contains(Headers.UPGRADE)) {
            throw UndertowMessages.MESSAGES.notAnUpgradeRequest();
        }
        UndertowLogger.REQUEST_LOGGER.debugf("Upgrading request %s", this);
        connection.setUpgradeListener(listener);
        setStatusCode(StatusCodes.SWITCHING_PROTOCOLS);
        getResponseHeaders().put(Headers.CONNECTION, Headers.UPGRADE_STRING);
        return this;
    }

    /**
     * Upgrade the channel to a raw socket. This method set the response code to 101, and then marks both the
     * request and response as terminated, which means that once the current request is completed the raw channel
     * can be obtained from {@link io.undertow.server.protocol.http.HttpServerConnection#getChannel()}
     *
     * @param productName the product name to report to the client
     * @throws IllegalStateException if a response or upgrade was already sent, or if the request body is already being
     *                               read
     */
    public HttpServerExchange upgradeChannel(String productName, final HttpUpgradeListener listener) {
        if (!connection.isUpgradeSupported()) {
            throw UndertowMessages.MESSAGES.upgradeNotSupported();
        }
        UndertowLogger.REQUEST_LOGGER.debugf("Upgrading request %s", this);
        connection.setUpgradeListener(listener);
        setStatusCode(StatusCodes.SWITCHING_PROTOCOLS);
        final HeaderMap headers = getResponseHeaders();
        headers.put(Headers.UPGRADE, productName);
        headers.put(Headers.CONNECTION, Headers.UPGRADE_STRING);
        return this;
    }

    /**
     *
     * @param connectListener
     * @return
     */
    public HttpServerExchange acceptConnectRequest(HttpUpgradeListener connectListener) {
        if(!getRequestMethod().equals(Methods.CONNECT)) {
            throw UndertowMessages.MESSAGES.notAConnectRequest();
        }
        connection.setConnectListener(connectListener);
        return this;
    }


    public HttpServerExchange addExchangeCompleteListener(final ExchangeCompletionListener listener) {
        if(isComplete() || this.exchangeCompletionListenersCount == -1) {
            throw UndertowMessages.MESSAGES.exchangeAlreadyComplete();
        }
        final int exchangeCompletionListenersCount = this.exchangeCompletionListenersCount++;
        ExchangeCompletionListener[] exchangeCompleteListeners = this.exchangeCompleteListeners;
        if (exchangeCompleteListeners == null || exchangeCompleteListeners.length == exchangeCompletionListenersCount) {
            ExchangeCompletionListener[] old = exchangeCompleteListeners;
            this.exchangeCompleteListeners = exchangeCompleteListeners = new ExchangeCompletionListener[exchangeCompletionListenersCount + 2];
            if(old != null) {
                System.arraycopy(old, 0, exchangeCompleteListeners, 0, exchangeCompletionListenersCount);
            }
        }
        exchangeCompleteListeners[exchangeCompletionListenersCount] = listener;
        return this;
    }

    public HttpServerExchange addDefaultResponseListener(final DefaultResponseListener listener) {
        int i = 0;
        if(defaultResponseListeners == null) {
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
     * @deprecated use either {@link #requestCookies()} or {@link #getRequestCookie(String)} or {@link #setRequestCookie(Cookie)} methods instead
     */
    @Deprecated
    public Map<String, Cookie> getRequestCookies() {
        if (deprecatedRequestCookies == null) {
            deprecatedRequestCookies = new MapDelegatingToSet((Set<Cookie>)((DelegatingIterable<Cookie>)requestCookies()).getDelegate());
        }
        return deprecatedRequestCookies;
    }

    /**
     * Sets a request cookie
     *
     * @param cookie The cookie
     */
    public HttpServerExchange setRequestCookie(final Cookie cookie) {
        if (cookie == null) return this;
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
        ((Set<Cookie>)((DelegatingIterable<Cookie>)requestCookies()).getDelegate()).add(cookie);
        return this;
    }

    public Cookie getRequestCookie(final String name) {
        if (name == null) return null;
        for (Cookie cookie : requestCookies()) {
            if (name.equals(cookie.getName())) {
                // TODO: QUESTION: Shouldn't we check instead of just name also
                // TODO  requestPath (stored in this exchange request path) and
                // TODO: domain (stored in Host HTTP header).
                return cookie;
            }
        }
        return null;
    }

    /**
     * Returns unmodifiable enumeration of request cookies.
     * @return A read-only enumeration of request cookies
     */
    public Iterable<Cookie> requestCookies() {
        if (requestCookies == null) {
            Set<Cookie> requestCookiesParam = new OverridableTreeSet<>();
            requestCookies = new DelegatingIterable<>(requestCookiesParam);
            Cookies.parseRequestCookies(
                    getConnection().getUndertowOptions().get(UndertowOptions.MAX_COOKIES, 200),
                    getConnection().getUndertowOptions().get(UndertowOptions.ALLOW_EQUALS_IN_COOKIE_VALUE, false),
                    requestHeaders.get(Headers.COOKIE), requestCookiesParam);
        }
        return requestCookies;
    }

    /**
     * Sets a response cookie
     *
     * @param cookie The cookie
     */
    public HttpServerExchange setResponseCookie(final Cookie cookie) {
        if (cookie == null) return this;
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
        ((Set<Cookie>)((DelegatingIterable<Cookie>)responseCookies()).getDelegate()).add(cookie);
        return this;
    }

    /**
     * @return A mutable map of response cookies
     * @deprecated use either {@link #responseCookies()} or {@link #setResponseCookie(Cookie)} methods instead
     */
    @Deprecated
    public Map<String, Cookie> getResponseCookies() {
        if (deprecatedResponseCookies == null) {
            deprecatedResponseCookies = new MapDelegatingToSet((Set<Cookie>)((DelegatingIterable<Cookie>)responseCookies()).getDelegate());
        }
        return deprecatedResponseCookies;
    }

    /**
     * Returns unmodifiable enumeration of response cookies.
     * @return A read-only enumeration of response cookies
     */
    public Iterable<Cookie> responseCookies() {
        if (responseCookies == null) {
            responseCookies = new DelegatingIterable<>(new OverridableTreeSet<>());
        }
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
            if(anyAreSet(state, FLAG_REQUEST_RESET)) {
                state &= ~FLAG_REQUEST_RESET;
                return requestChannel;
            }
            return null;
        }
        if (anyAreSet(state, FLAG_REQUEST_TERMINATED)) {
            return requestChannel = new ReadDispatchChannel(new ConduitStreamSourceChannel(Configurable.EMPTY, new EmptyStreamSourceConduit(getIoThread())));
        }
        final ConduitWrapper<StreamSourceConduit>[] wrappers = this.requestWrappers;
        final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
        if (wrappers != null) {
            this.requestWrappers = null;
            final WrapperConduitFactory<StreamSourceConduit> factory = new WrapperConduitFactory<>(wrappers, requestWrapperCount, sourceChannel.getConduit(), this);
            sourceChannel.setConduit(factory.create());
        }
        return requestChannel = new ReadDispatchChannel(sourceChannel);
    }

    void resetRequestChannel() {
        state |= FLAG_REQUEST_RESET;
    }

    public boolean isRequestChannelAvailable() {
        return requestChannel == null || anyAreSet(state, FLAG_REQUEST_RESET);
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
        PooledByteBuffer[] data = getAttachment(BUFFERED_REQUEST_DATA);
        if(data != null) {
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
            exchangeCompletionListenersCount = -1;
            next.exchangeEvent(this, new ExchangeCompleteNextListener(exchangeCompleteListeners, this, i));
        } else if (exchangeCompletionListenersCount == 0) {
            exchangeCompletionListenersCount = -1;
            connection.exchangeComplete(this);
        }
    }

    /**
     * Get the response channel. The channel must be closed and fully flushed before the next response can be started.
     * In order to close the channel you must first call {@link org.xnio.channels.StreamSinkChannel#shutdownWrites()},
     * and then call {@link org.xnio.channels.StreamSinkChannel#flush()} until it returns true. Alternatively you can
     * call {@link #endExchange()}, which will close the channel as part of its cleanup.
     * <p>
     * Closing a fixed-length response before the corresponding number of bytes has been written will cause the connection
     * to be reset and subsequent requests to fail; thus it is important to ensure that the proper content length is
     * delivered when one is specified.  The response channel may not be writable until after the response headers have
     * been sent.
     * <p>
     * If this method is not called then an empty or default response body will be used, depending on the response code set.
     * <p>
     * The returned channel will begin to write out headers when the first write request is initiated, or when
     * {@link org.xnio.channels.StreamSinkChannel#shutdownWrites()} is called on the channel with no content being written.
     * Once the channel is acquired, however, the response code and headers may not be modified.
     * <p>
     *
     * @return the response channel, or {@code null} if another party already acquired the channel
     */
    public StreamSinkChannel getResponseChannel() {
        if (responseChannel != null) {
            return null;
        }
        final ConduitWrapper<StreamSinkConduit>[] wrappers = responseWrappers;
        this.responseWrappers = null;
        final ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
        if (sinkChannel == null) {
            return null;
        }
        if(wrappers != null) {
            final WrapperStreamSinkConduitFactory factory = new WrapperStreamSinkConduitFactory(wrappers, responseWrapperCount, this, sinkChannel.getConduit());
            sinkChannel.setConduit(factory.create());
        } else {
            sinkChannel.setConduit(connection.getSinkConduit(this, sinkChannel.getConduit()));
        }
        this.responseChannel = new WriteDispatchChannel(sinkChannel);
        this.startResponse();
        return responseChannel;
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
        if(blockingHttpExchange != null) {
            return blockingHttpExchange.getReceiver();
        }
        if(receiver != null) {
            return receiver;
        }
        return receiver = new AsyncReceiverImpl(this);
    }

    /**
     * @return <code>true</code> if {@link #getResponseChannel()} has not been called
     */
    public boolean isResponseChannelAvailable() {
        return responseChannel == null;
    }


    /**
     * Get the status code.
     *
     * @see #getStatusCode()
     * @return the status code
     */
    @Deprecated
    public int getResponseCode() {
        return state & MASK_RESPONSE_CODE;
    }

    /**
     * Change the status code for this response.  If not specified, the code will be a {@code 200}.  Setting
     * the status code after the response headers have been transmitted has no effect.
     *
     * @see #setStatusCode(int)
     * @param statusCode the new code
     * @throws IllegalStateException if a response or upgrade was already sent
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
        if(statusCode >= 500) {
            if(UndertowLogger.ERROR_RESPONSE.isDebugEnabled()) {
                UndertowLogger.ERROR_RESPONSE.debugf(new RuntimeException(), "Setting error code %s for exchange %s", statusCode, this);
            }
        }
        this.state = oldVal & ~MASK_RESPONSE_CODE | statusCode & MASK_RESPONSE_CODE;
        return this;
    }

    /**
     * Sets the HTTP reason phrase. Depending on the protocol this may or may not be honoured. In particular HTTP2
     * has removed support for the reason phrase.
     *
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
     *
     * @return The current reason phrase
     */
    public String getReasonPhrase() {
        return getAttachment(REASON_PHRASE);
    }

    /**
     * Adds a {@link ConduitWrapper} to the request wrapper chain.
     *
     * @param wrapper the wrapper
     */
    public HttpServerExchange addRequestWrapper(final ConduitWrapper<StreamSourceConduit> wrapper) {
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
        return this;
    }

    /**
     * Adds a {@link ConduitWrapper} to the response wrapper chain.
     *
     * @param wrapper the wrapper
     */
    public HttpServerExchange addResponseWrapper(final ConduitWrapper<StreamSinkConduit> wrapper) {
        ConduitWrapper<StreamSinkConduit>[] wrappers = responseWrappers;
        if (responseChannel != null) {
            throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
        }
        if(wrappers == null) {
            this.responseWrappers = wrappers = new ConduitWrapper[2];
        } else if (wrappers.length == responseWrapperCount) {
            responseWrappers = new ConduitWrapper[wrappers.length + 2];
            System.arraycopy(wrappers, 0, responseWrappers, 0, wrappers.length);
            wrappers = responseWrappers;
        }
        wrappers[responseWrapperCount++] = wrapper;
        return this;
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
        if(responseChannel != null) {
            responseChannel.responseDone();
        }
        this.state = oldVal | FLAG_RESPONSE_TERMINATED;
        if (anyAreSet(oldVal, FLAG_REQUEST_TERMINATED)) {
            invokeExchangeCompleteListeners();
        }
        return this;
    }

    /**
     * @return The request start time using the JVM's high-resolution time source,
     * in nanoseconds, or -1 if this was not recorded
     * @see UndertowOptions#RECORD_REQUEST_START_TIME
     * @see Connectors#setRequestStartTime(HttpServerExchange)
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
            if(blockingHttpExchange != null) {
                //we still have to close the blocking exchange in this case,
                IoUtils.safeClose(blockingHttpExchange);
            }
            return this;
        }
        if(defaultResponseListeners != null) {
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

        if (anyAreClear(state, FLAG_REQUEST_TERMINATED)) {
            connection.terminateRequestChannel(this);
        }

        if (blockingHttpExchange != null) {
            try {
                //TODO: can we end up in this situation in a IO thread?
                blockingHttpExchange.close();
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(connection);
            } catch (Throwable t) {
                UndertowLogger.REQUEST_IO_LOGGER.handleUnexpectedFailure(t);
                IoUtils.safeClose(connection);
            }
        }

        //417 means that we are rejecting the request
        //so the client should not actually send any data
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

                        if (getStatusCode() != StatusCodes.EXPECTATION_FAILED || totalRead > 0) {
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

                                            //make sure the listeners have been invoked
                                            //unless the connection has been killed this is a no-op
                                            terminateRequest();
                                            terminateResponse();
                                            UndertowLogger.REQUEST_LOGGER.debug("Exception draining request stream", e);
                                            IoUtils.safeClose(connection);
                                        }
                                    }
                            ));
                            requestChannel.resumeReads();
                            return this;
                        } else {
                            break;
                        }
                    } else if (read == -1) {
                        break;
                    }
                } catch (Throwable t) {
                    if (t instanceof IOException) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException((IOException) t);
                    } else {
                        UndertowLogger.REQUEST_IO_LOGGER.handleUnexpectedFailure(t);
                    }
                    invokeExchangeCompleteListeners();
                    IoUtils.safeClose(connection);
                    return this;
                }

            }
        }
        if (anyAreClear(state, FLAG_RESPONSE_TERMINATED)) {
            closeAndFlushResponse();
        }
        return this;
    }

    private void closeAndFlushResponse() {
        if(!connection.isOpen()) {
            //not much point trying to flush

            //make sure the listeners have been invoked
            terminateRequest();
            terminateResponse();
            return;
        }
        try {
            if (isResponseChannelAvailable()) {
                if(!getRequestMethod().equals(Methods.CONNECT) && !(getRequestMethod().equals(Methods.HEAD) && getResponseHeaders().contains(Headers.CONTENT_LENGTH)) && Connectors.isEntityBodyAllowed(this)) {
                    //according to
                    getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
                }
                getResponseChannel();
            } else if (anyAreClear(state, FLAG_RESPONSE_TERMINATED) && !responseChannel.isOpen()) {
                // UNDERTOW-1664: Http/2 response channels may be closed prior to the connection. There's
                // no reason to attempt to flush a response for a closed channel but we must ensure
                // the listeners have been invoked.
                invokeExchangeCompleteListeners();
                IoUtils.safeClose(connection);
                return;
            }
            responseChannel.shutdownWrites();
            if (!responseChannel.flush()) {
                responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                        new ChannelListener<StreamSinkChannel>() {
                            @Override
                            public void handleEvent(final StreamSinkChannel channel) {
                                channel.suspendWrites();
                                channel.getWriteSetter().set(null);
                                //defensive programming, should never happen
                                if (anyAreClear(state, FLAG_RESPONSE_TERMINATED)) {
                                    //make sure the listeners have been invoked
                                    invokeExchangeCompleteListeners();
                                    UndertowLogger.ROOT_LOGGER.responseWasNotTerminated(connection, HttpServerExchange.this);
                                    IoUtils.safeClose(connection);
                                }
                            }
                        }, new ChannelExceptionHandler<Channel>() {
                            @Override
                            public void handleException(final Channel channel, final IOException exception) {
                                //make sure the listeners have been invoked
                                invokeExchangeCompleteListeners();
                                UndertowLogger.REQUEST_LOGGER.debug("Exception ending request", exception);
                                IoUtils.safeClose(connection);
                            }
                        }
                ));
                responseChannel.resumeWrites();
            } else {
                //defensive programming, should never happen
                if (anyAreClear(state, FLAG_RESPONSE_TERMINATED)) {
                    //make sure the listeners have been invoked
                    invokeExchangeCompleteListeners();
                    UndertowLogger.ROOT_LOGGER.responseWasNotTerminated(connection, this);
                    IoUtils.safeClose(connection);
                }
            }
        } catch (Throwable t) {
            if (t instanceof IOException) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException((IOException) t);
            } else {
                UndertowLogger.REQUEST_IO_LOGGER.handleUnexpectedFailure(t);
            }
            invokeExchangeCompleteListeners();

            IoUtils.safeClose(connection);
        }
    }

    /**
     * Transmit the response headers. After this method successfully returns,
     * the response channel may become writable.
     * <p>
     * If this method fails the request and response channels will be closed.
     * <p>
     * This method runs asynchronously. If the channel is writable it will
     * attempt to write as much of the response header as possible, and then
     * queue the rest in a listener and return.
     * <p>
     * If future handlers in the chain attempt to write before this is finished
     * XNIO will just magically sort it out so it works. This is not actually
     * implemented yet, so we just terminate the connection straight away at
     * the moment.
     * <p>
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

    public XnioIoThread getIoThread() {
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
        if (!isRequestChannelAvailable()) {
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
        if(sm != null) {
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

        //technically it is possible to modify the exchange after the response conduit has been created
        //as the response channel should not be retrieved until it is about to be written to
        //if we get complaints about this we can add support for it, however it makes the exchange bigger and the connectors more complex
        addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
                listener.beforeCommit(exchange);
                return factory.create();
            }
        });
    }

    /**
     * Actually resumes reads or writes, if the relevant method has been called.
     *
     * @return <code>true</code> if reads or writes were resumed
     */
    boolean runResumeReadWrite() {
        boolean ret = false;
        if(anyAreSet(state, FLAG_SHOULD_RESUME_WRITES)) {
            responseChannel.runResume();
            ret = true;
        }
        if(anyAreSet(state, FLAG_SHOULD_RESUME_READS)) {
            requestChannel.runResume();
            ret = true;
        }
        return ret;
    }

    boolean isResumed() {
        return anyAreSet(state, FLAG_SHOULD_RESUME_WRITES | FLAG_SHOULD_RESUME_READS);
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
            } else if(i == -1) {
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
            return new BlockingReceiverImpl(exchange, getInputStream());
        }
    }

    /**
     * Channel implementation that is actually provided to clients of the exchange.
     * <p>
     * We do not provide the underlying conduit channel, as this is shared between requests, so we need to make sure that after this request
     * is done the the channel cannot affect the next request.
     * <p>
     * It also delays a wakeup/resumesWrites calls until the current call stack has returned, thus ensuring that only 1 thread is
     * active in the exchange at any one time.
     */
    private class WriteDispatchChannel extends DetachableStreamSinkChannel implements StreamSinkChannel {

        private boolean wakeup;

        WriteDispatchChannel(final ConduitStreamSinkChannel delegate) {
            super(delegate);
        }

        @Override
        protected boolean isFinished() {
            return allAreSet(state, FLAG_RESPONSE_TERMINATED);
        }

        @Override
        public void resumeWrites() {
            if (isInCall()) {
                state |= FLAG_SHOULD_RESUME_WRITES;
                if(anyAreSet(state, FLAG_DISPATCHED)) {
                    throw UndertowMessages.MESSAGES.resumedAndDispatched();
                }
            } else if(!isFinished()){
                delegate.resumeWrites();
            }
        }

        @Override
        public void suspendWrites() {
            state &= ~FLAG_SHOULD_RESUME_WRITES;
            super.suspendWrites();
        }

        @Override
        public void wakeupWrites() {
            if (isFinished()) {
                return;
            }
            if (isInCall()) {
                wakeup = true;
                state |= FLAG_SHOULD_RESUME_WRITES;
                if(anyAreSet(state, FLAG_DISPATCHED)) {
                    throw UndertowMessages.MESSAGES.resumedAndDispatched();
                }
            } else {
                delegate.wakeupWrites();
            }
        }

        @Override
        public boolean isWriteResumed() {
            return anyAreSet(state, FLAG_SHOULD_RESUME_WRITES) || super.isWriteResumed();
        }

        public void runResume() {
            if (isWriteResumed()) {
                if(isFinished()) {
                    invokeListener();
                } else {
                    if (wakeup) {
                        wakeup = false;
                        state &= ~FLAG_SHOULD_RESUME_WRITES;
                        delegate.wakeupWrites();
                    } else {
                        state &= ~FLAG_SHOULD_RESUME_WRITES;
                        delegate.resumeWrites();
                    }
                }
            } else if(wakeup) {
                wakeup = false;
                invokeListener();
            }
        }

        private void invokeListener() {
            if(writeSetter != null) {
                super.getIoThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        ChannelListeners.invokeChannelListener(WriteDispatchChannel.this, writeSetter.get());
                    }
                });
            }
        }

        @Override
        public void awaitWritable() throws IOException {
            if(Thread.currentThread() == super.getIoThread()) {
                throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
            }
            super.awaitWritable();
        }

        @Override
        public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
            if(Thread.currentThread() == super.getIoThread()) {
                throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
            }
            super.awaitWritable(time, timeUnit);
        }

        @Override
        public long transferFrom(FileChannel src, long position, long count) throws IOException {
            long l = super.transferFrom(src, position, count);
            if(l > 0) {
                responseBytesSent += l;
            }
            return l;
        }

        @Override
        public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
            long l = super.transferFrom(source, count, throughBuffer);
            if(l > 0) {
                responseBytesSent += l;
            }
            return l;
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            long l = super.write(srcs, offset, length);
            responseBytesSent += l;
            return l;
        }

        @Override
        public long write(ByteBuffer[] srcs) throws IOException {
            long l = super.write(srcs);
            responseBytesSent += l;
            return l;
        }

        @Override
        public int writeFinal(ByteBuffer src) throws IOException {
            int l = super.writeFinal(src);
            responseBytesSent += l;
            return l;
        }

        @Override
        public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
            long l = super.writeFinal(srcs, offset, length);
            responseBytesSent += l;
            return l;
        }

        @Override
        public long writeFinal(ByteBuffer[] srcs) throws IOException {
            long l = super.writeFinal(srcs);
            responseBytesSent += l;
            return l;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            int l = super.write(src);
            responseBytesSent += l;
            return l;
        }
    }

    /**
     * Channel implementation that is actually provided to clients of the exchange. We do not provide the underlying
     * conduit channel, as this will become the next requests conduit channel, so if a thread is still hanging onto this
     * exchange it can result in problems.
     * <p>
     * It also delays a readResume call until the current call stack has returned, thus ensuring that only 1 thread is
     * active in the exchange at any one time.
     * <p>
     * It also handles buffered request data.
     */
    private final class ReadDispatchChannel extends DetachableStreamSourceChannel implements StreamSourceChannel {

        private boolean wakeup = true;
        private boolean readsResumed = false;


        ReadDispatchChannel(final ConduitStreamSourceChannel delegate) {
            super(delegate);
        }

        @Override
        protected boolean isFinished() {
            return allAreSet(state, FLAG_REQUEST_TERMINATED);
        }

        @Override
        public void resumeReads() {
            readsResumed = true;
            if (isInCall()) {
                state |= FLAG_SHOULD_RESUME_READS;
                if(anyAreSet(state, FLAG_DISPATCHED)) {
                    throw UndertowMessages.MESSAGES.resumedAndDispatched();
                }
            } else if (!isFinished()) {
                delegate.resumeReads();
            }

        }

        public void wakeupReads() {
            if (isInCall()) {
                wakeup = true;
                state |= FLAG_SHOULD_RESUME_READS;
                if(anyAreSet(state, FLAG_DISPATCHED)) {
                    throw UndertowMessages.MESSAGES.resumedAndDispatched();
                }
            } else {
                if(isFinished()) {
                    invokeListener();
                } else {
                    delegate.wakeupReads();
                }
            }
        }

        private void invokeListener() {
            if(readSetter != null) {
                super.getIoThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        ChannelListeners.invokeChannelListener(ReadDispatchChannel.this, readSetter.get());
                    }
                });
            }
        }

        public void requestDone() {
            if(delegate instanceof ConduitStreamSourceChannel) {
                ((ConduitStreamSourceChannel)delegate).setReadListener(null);
                ((ConduitStreamSourceChannel)delegate).setCloseListener(null);
            } else {
                delegate.getReadSetter().set(null);
                delegate.getCloseSetter().set(null);
            }
        }

        @Override
        public long transferTo(long position, long count, FileChannel target) throws IOException {
            PooledByteBuffer[] buffered = getAttachment(BUFFERED_REQUEST_DATA);
            if (buffered == null) {
                return super.transferTo(position, count, target);
            }
            return target.transferFrom(this, position, count);
        }

        @Override
        public void awaitReadable() throws IOException {
            if(Thread.currentThread() == super.getIoThread()) {
                throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
            }
            PooledByteBuffer[] buffered = getAttachment(BUFFERED_REQUEST_DATA);
            if (buffered == null) {
                super.awaitReadable();
            }
        }

        @Override
        public void suspendReads() {
            readsResumed = false;
            state &= ~(FLAG_SHOULD_RESUME_READS);
            super.suspendReads();
        }

        @Override
        public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
            PooledByteBuffer[] buffered = getAttachment(BUFFERED_REQUEST_DATA);
            if (buffered == null) {
                return super.transferTo(count, throughBuffer, target);
            }
            //make sure there is no garbage in throughBuffer
            throughBuffer.position(0);
            throughBuffer.limit(0);
            long copied = 0;
            for (int i = 0; i < buffered.length; ++i) {
                PooledByteBuffer pooled = buffered[i];
                if (pooled != null) {
                    final ByteBuffer buf = pooled.getBuffer();
                    if (buf.hasRemaining()) {
                        int res = target.write(buf);

                        if (!buf.hasRemaining()) {
                            pooled.close();
                            buffered[i] = null;
                        }
                        if (res == 0) {
                            return copied;
                        } else {
                            copied += res;
                        }
                    } else {
                        pooled.close();
                        buffered[i] = null;
                    }
                }
            }
            removeAttachment(BUFFERED_REQUEST_DATA);
            if (copied == 0) {
                return super.transferTo(count, throughBuffer, target);
            } else {
                return copied;
            }
        }

        @Override
        public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
            if(Thread.currentThread() == super.getIoThread()) {
                throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
            }
            PooledByteBuffer[] buffered = getAttachment(BUFFERED_REQUEST_DATA);
            if (buffered == null) {
                super.awaitReadable(time, timeUnit);
            }
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            PooledByteBuffer[] buffered = getAttachment(BUFFERED_REQUEST_DATA);
            if (buffered == null) {
                return super.read(dsts, offset, length);
            }
            long copied = 0;
            for (int i = 0; i < buffered.length; ++i) {
                PooledByteBuffer pooled = buffered[i];
                if (pooled != null) {
                    final ByteBuffer buf = pooled.getBuffer();
                    if (buf.hasRemaining()) {
                        copied += Buffers.copy(dsts, offset, length, buf);
                        if (!buf.hasRemaining()) {
                            pooled.close();
                            buffered[i] = null;
                        }
                        if (!Buffers.hasRemaining(dsts, offset, length)) {
                            return copied;
                        }
                    } else {
                        pooled.close();
                        buffered[i] = null;
                    }
                }
            }
            removeAttachment(BUFFERED_REQUEST_DATA);
            if (copied == 0) {
                return super.read(dsts, offset, length);
            } else {
                return copied;
            }
        }

        @Override
        public long read(ByteBuffer[] dsts) throws IOException {
            return read(dsts, 0, dsts.length);
        }

        @Override
        public boolean isOpen() {
            PooledByteBuffer[] buffered = getAttachment(BUFFERED_REQUEST_DATA);
            if (buffered != null) {
                return true;
            }
            return super.isOpen();
        }

        @Override
        public void close() throws IOException {
            PooledByteBuffer[] buffered = getAttachment(BUFFERED_REQUEST_DATA);
            if (buffered != null) {
                for (PooledByteBuffer pooled : buffered) {
                    if (pooled != null) {
                        pooled.close();
                    }
                }
            }
            removeAttachment(BUFFERED_REQUEST_DATA);
            super.close();
        }

        @Override
        public boolean isReadResumed() {
            PooledByteBuffer[] buffered = getAttachment(BUFFERED_REQUEST_DATA);
            if (buffered != null) {
                return readsResumed;
            }
            if(isFinished()) {
                return false;
            }
            return anyAreSet(state, FLAG_SHOULD_RESUME_READS) || super.isReadResumed();
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            PooledByteBuffer[] buffered = getAttachment(BUFFERED_REQUEST_DATA);
            if (buffered == null) {
                return super.read(dst);
            }
            int copied = 0;
            for (int i = 0; i < buffered.length; ++i) {
                PooledByteBuffer pooled = buffered[i];
                if (pooled != null) {
                    final ByteBuffer buf = pooled.getBuffer();
                    if (buf.hasRemaining()) {
                        copied += Buffers.copy(dst, buf);
                        if (!buf.hasRemaining()) {
                            pooled.close();
                            buffered[i] = null;
                        }
                        if (!dst.hasRemaining()) {
                            return copied;
                        }
                    } else {
                        pooled.close();
                        buffered[i] = null;
                    }
                }
            }
            removeAttachment(BUFFERED_REQUEST_DATA);
            if (copied == 0) {
                return super.read(dst);
            } else {
                return copied;
            }
        }

        public void runResume() {
            if (isReadResumed()) {
                if(isFinished()) {
                    invokeListener();
                } else {
                    if (wakeup) {
                        wakeup = false;
                        state &= ~FLAG_SHOULD_RESUME_READS;
                        delegate.wakeupReads();
                    } else {
                        state &= ~FLAG_SHOULD_RESUME_READS;
                        delegate.resumeReads();
                    }
                }
            } else if(wakeup) {
                wakeup = false;
                invokeListener();
            }
        }
    }

    public static class WrapperStreamSinkConduitFactory implements ConduitFactory<StreamSinkConduit> {

        private final HttpServerExchange exchange;
        private final ConduitWrapper<StreamSinkConduit>[] wrappers;
        private int position;
        private final StreamSinkConduit first;


        public WrapperStreamSinkConduitFactory(ConduitWrapper<StreamSinkConduit>[] wrappers, int wrapperCount, HttpServerExchange exchange, StreamSinkConduit first) {
            this.wrappers = wrappers;
            this.exchange = exchange;
            this.first = first;
            this.position = wrapperCount - 1;
        }

        @Override
        public StreamSinkConduit create() {
            if (position == -1) {
                return exchange.getConnection().getSinkConduit(exchange, first);
            } else {
                return wrappers[position--].wrap(this, exchange);
            }
        }
    }

    public static class WrapperConduitFactory<T extends Conduit> implements ConduitFactory<T> {

        private final HttpServerExchange exchange;
        private final ConduitWrapper<T>[] wrappers;
        private int position;
        private T first;


        public WrapperConduitFactory(ConduitWrapper<T>[] wrappers, int wrapperCount, T first, HttpServerExchange exchange) {
            this.wrappers = wrappers;
            this.exchange = exchange;
            this.position = wrapperCount - 1;
            this.first = first;
        }

        @Override
        public T create() {
            if (position == -1) {
                return first;
            } else {
                return wrappers[position--].wrap(this, exchange);
            }
        }
    }

    @Override
    public String toString() {
        return "HttpServerExchange{ " + getRequestMethod().toString() + " " + getRequestURI() + '}';
    }
}
