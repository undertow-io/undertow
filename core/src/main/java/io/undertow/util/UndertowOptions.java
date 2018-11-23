/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;


import javax.security.sasl.Sasl;

import io.undertow.util.UndertowOption;

/**
 * @author Stuart Douglas
 */
public class UndertowOptions {

    /**
     * The maximum size in bytes of a http request header.
     */
    public static final UndertowOption<Integer> MAX_HEADER_SIZE = UndertowOption.create("MAX_HEADER_SIZE", Integer.class);
    /**
     * The default size we allow for the HTTP header.
     */
    public static final int DEFAULT_MAX_HEADER_SIZE = 1024 * 1024;

    /**
     * The default maximum size of the HTTP entity body.
     */
    public static final UndertowOption<Long> MAX_ENTITY_SIZE = UndertowOption.create("MAX_ENTITY_SIZE", Long.class);

    /**
     * The default maximum size of the HTTP entity body when using the mutiltipart parser. Generall this will be larger than {@link #MAX_ENTITY_SIZE}.
     * <p>
     * If this is not specified it will be the same as {@link #MAX_ENTITY_SIZE}.
     */
    public static final UndertowOption<Long> MULTIPART_MAX_ENTITY_SIZE = UndertowOption.create("MULTIPART_MAX_ENTITY_SIZE", Long.class);

    /**
     * We do not have a default upload limit
     */
    public static final long DEFAULT_MAX_ENTITY_SIZE = -1;

    /**
     * If we should buffer pipelined requests. Defaults to false.
     */
    public static final UndertowOption<Boolean> BUFFER_PIPELINED_DATA = UndertowOption.create("BUFFER_PIPELINED_DATA", Boolean.class);

    /**
     * The idle timeout in milliseconds after which the channel will be closed.
     * <p>
     * If the underlying channel already has a read or write timeout set the smaller of the two values will be used
     * for read/write timeouts.
     */
    public static final UndertowOption<Integer> IDLE_TIMEOUT = UndertowOption.create("IDLE_TIMEOUT", Integer.class);

    /**
     * The maximum allowed time of reading HTTP request in milliseconds.
     * <p>
     * <code>-1</code> or missing value disables this functionality.
     */
    public static final UndertowOption<Integer> REQUEST_PARSE_TIMEOUT = UndertowOption.create("REQUEST_PARSE_TIMEOUT", Integer.class);

    /**
     * The amount of time the connection can be idle with no current requests before it is closed;
     */
    public static final UndertowOption<Integer> NO_REQUEST_TIMEOUT = UndertowOption.create("NO_REQUEST_TIMEOUT", Integer.class);

    public static final int DEFAULT_MAX_PARAMETERS = 1000;

    /**
     * The maximum number of parameters that will be parsed. This is used to protect against hash vulnerabilities.
     * <p>
     * This applies to both query parameters, and to POST data, but is not cumulative (i.e. you can potentially have
     * max parameters * 2 total parameters).
     * <p>
     * Defaults to 1000
     */
    public static final UndertowOption<Integer> MAX_PARAMETERS = UndertowOption.create("MAX_PARAMETERS", Integer.class);

    public static final int DEFAULT_MAX_HEADERS = 200;

    /**
     * The maximum number of headers that will be parsed. This is used to protect against hash vulnerabilities.
     * <p>
     * Defaults to 200
     */
    public static final UndertowOption<Integer> MAX_HEADERS = UndertowOption.create("MAX_HEADERS", Integer.class);


    /**
     * The maximum number of cookies that will be parsed. This is used to protect against hash vulnerabilities.
     * <p>
     * Defaults to 200
     */
    public static final UndertowOption<Integer> MAX_COOKIES = UndertowOption.create("MAX_COOKIES", Integer.class);

    /**
     * If a request comes in with encoded / characters (i.e. %2F), will these be decoded.
     * <p>
     * This can cause security problems if a front end proxy does not perform the same decoding, and as a result
     * this is disabled by default.
     * <p>
     * Defaults to false
     * <p>
     * See <a href="http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2007-0450">CVE-2007-0450</a>
     */
    public static final UndertowOption<Boolean> ALLOW_ENCODED_SLASH = UndertowOption.create("ALLOW_ENCODED_SLASH", Boolean.class);

    /**
     * If this is true then the parser will decode the URL and query parameters using the selected character encoding (UTF-8 by default). If this is false they will
     * not be decoded. This will allow a later handler to decode them into whatever charset is desired.
     * <p>
     * Defaults to true.
     */
    public static final UndertowOption<Boolean> DECODE_URL = UndertowOption.create("DECODE_URL", Boolean.class);


    /**
     * If this is true then the parser will decode the URL and query parameters using the selected character encoding (UTF-8 by default). If this is false they will
     * not be decoded. This will allow a later handler to decode them into whatever charset is desired.
     * <p>
     * Defaults to true.
     */
    public static final UndertowOption<String> URL_CHARSET = UndertowOption.create("URL_CHARSET", String.class);

    /**
     * If this is true then a Connection: keep-alive header will be added to responses, even when it is not strictly required by
     * the specification.
     * <p>
     * Defaults to true
     */
    public static final UndertowOption<Boolean> ALWAYS_SET_KEEP_ALIVE = UndertowOption.create("ALWAYS_SET_KEEP_ALIVE", Boolean.class);

    /**
     * If this is true then a Date header will be added to all responses. The HTTP spec says this header should be added to all
     * responses, unless the server does not have an accurate clock.
     * <p>
     * Defaults to true
     */
    public static final UndertowOption<Boolean> ALWAYS_SET_DATE = UndertowOption.create("ALWAYS_SET_DATE", Boolean.class);

    /**
     * Maximum size of a buffered request, in bytes
     * <p>
     * Requests are not usually buffered, the most common case is when performing SSL renegotiation for a POST request, and the post data must be fully
     * buffered in order to perform the renegotiation.
     * <p>
     * Defaults to 16384.
     */
    public static final UndertowOption<Integer> MAX_BUFFERED_REQUEST_SIZE = UndertowOption.create("MAX_BUFFERED_REQUEST_SIZE", Integer.class);

    public static final int DEFAULT_MAX_BUFFERED_REQUEST_SIZE = 16384;

    /**
     * If this is true then Undertow will record the request start time, to allow for request time to be logged
     * <p>
     * This has a small but measurable performance impact
     * <p>
     * default is false
     */
    public static final UndertowOption<Boolean> RECORD_REQUEST_START_TIME = UndertowOption.create("RECORD_REQUEST_START_TIME", Boolean.class);

    /**
     * If this is true then Undertow will allow non-escaped equals characters in unquoted cookie values.
     * <p>
     * Unquoted cookie values may not contain equals characters. If present the value ends before the equals sign. The remainder of the cookie value will be dropped.
     * <p>
     * default is false
     */
    public static final UndertowOption<Boolean> ALLOW_EQUALS_IN_COOKIE_VALUE = UndertowOption.create("ALLOW_EQUALS_IN_COOKIE_VALUE", Boolean.class);

    /**
     * If this is true then Undertow will enable RFC6265 compliant cookie validation for Set-Cookie header instead of legacy backward compatible behavior.
     * <p>
     * default is false
     */
    public static final UndertowOption<Boolean> ENABLE_RFC6265_COOKIE_VALIDATION = UndertowOption.create("ENABLE_RFC6265_COOKIE_VALIDATION", Boolean.class);

    public static final boolean DEFAULT_ENABLE_RFC6265_COOKIE_VALIDATION = false;

    /**
     * If we should attempt to use HTTP2 for HTTPS connections.
     */
    public static final UndertowOption<Boolean> ENABLE_HTTP2 = UndertowOption.create("ENABLE_HTTP2", Boolean.class);

    /**
     * If connector level statistics should be enabled. This has a slight performance impact, but allows statistics such
     * as bytes sent/recevied to be monitored.
     * <p>
     * If this is passed to the client then client statistics will be enabled.
     */
    public static final UndertowOption<Boolean> ENABLE_STATISTICS = UndertowOption.create("ENABLE_STATISTICS", Boolean.class);


    /**
     * If unknown protocols should be allowed. The known protocols are:
     * <p>
     * HTTP/0.9
     * HTTP/1.0
     * HTTP/1.1
     * HTTP/2.0
     * <p>
     * If this is false then requests that specify any other protocol will be rejected with a 400
     * <p>
     * Defaults to false
     */
    public static final UndertowOption<Boolean> ALLOW_UNKNOWN_PROTOCOLS = UndertowOption.create("ALLOW_UNKNOWN_PROTOCOLS", Boolean.class);

    /**
     * The size of the header table that is used in the encoder
     */
    public static final UndertowOption<Integer> HTTP2_SETTINGS_HEADER_TABLE_SIZE = UndertowOption.create("HTTP2_SETTINGS_HEADER_TABLE_SIZE", Integer.class);
    public static final int HTTP2_SETTINGS_HEADER_TABLE_SIZE_DEFAULT = 4096;

    /**
     * If push should be enabled for this connection.
     */
    public static final UndertowOption<Boolean> HTTP2_SETTINGS_ENABLE_PUSH = UndertowOption.create("HTTP2_SETTINGS_ENABLE_PUSH", Boolean.class);

    /**
     * The maximum number of concurrent
     */
    public static final UndertowOption<Integer> HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS = UndertowOption.create("HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS", Integer.class);

    public static final UndertowOption<Integer> HTTP2_SETTINGS_INITIAL_WINDOW_SIZE = UndertowOption.create("HTTP2_SETTINGS_INITIAL_WINDOW_SIZE", Integer.class);
    public static final UndertowOption<Integer> HTTP2_SETTINGS_MAX_FRAME_SIZE = UndertowOption.create("HTTP2_SETTINGS_MAX_FRAME_SIZE", Integer.class);

    /**
     * Deprecated, as it is effectively a duplicate of MAX_HEADER_SIZE
     *
     * @see #MAX_HEADER_SIZE
     */
    @Deprecated
    public static final UndertowOption<Integer> HTTP2_SETTINGS_MAX_HEADER_LIST_SIZE = UndertowOption.create("HTTP2_SETTINGS_MAX_HEADER_LIST_SIZE", Integer.class);

    /**
     * The maximum amount of padding to send in a HTTP/2 frame. Actual amount will be randomly determined, defaults to Zero.
     */
    public static final UndertowOption<Integer> HTTP2_PADDING_SIZE = UndertowOption.create("HTTP2_PADDING_SIZE", Integer.class);

    /**
     * Undertow keeps a LRU cache of common huffman encodings. This sets the maximum size, setting this to 0 will disable the caching.
     */
    public static final UndertowOption<Integer> HTTP2_HUFFMAN_CACHE_SIZE = UndertowOption.create("HTTP2_HUFFMAN_CACHE_SIZE", Integer.class);

    /**
     * The maximum number of concurrent requests that will be processed at a time. This differs from max concurrent streams in that it is not sent to the remote client.
     * <p>
     * If the number of pending requests exceeds this number then requests will be queued, the difference between this and max concurrent streams determines
     * the maximum number of requests that will be queued.
     * <p>
     * Queued requests are processed by a priority queue, rather than a FIFO based queue, using HTTP2 stream priority.
     * <p>
     * If this number is smaller than or equal to zero then max concurrent streams determines the maximum number of streams that can be run.
     */
    public static final UndertowOption<Integer> MAX_CONCURRENT_REQUESTS_PER_CONNECTION = UndertowOption.create("MAX_CONCURRENT_REQUESTS_PER_CONNECTION", Integer.class);

    /**
     * The maximum number of buffers that will be used before reads are paused in framed protocols. Defaults to 10
     */
    public static final UndertowOption<Integer> MAX_QUEUED_READ_BUFFERS = UndertowOption.create("MAX_QUEUED_READ_BUFFERS", Integer.class);

    /**
     * The maximum AJP packet size, default is 8192
     */
    public static final UndertowOption<Integer> MAX_AJP_PACKET_SIZE = UndertowOption.create("MAX_AJP_PACKET_SIZE", Integer.class);

    /**
     * If this is true then HTTP/1.1 requests will be failed if no host header is present.
     */
    public static final UndertowOption<Boolean> REQUIRE_HOST_HTTP11 = UndertowOption.create("REQUIRE_HOST_HTTP11", Boolean.class);

    public static final int DEFAULT_MAX_CACHED_HEADER_SIZE = 150;

    /**
     * The maximum size of a header name+value combo that is cached in the per connection cache. Defaults to 150
     */
    public static final UndertowOption<Integer> MAX_CACHED_HEADER_SIZE = UndertowOption.create("MAX_CACHED_HEADER_SIZE", Integer.class);

    public static final int DEFAULT_HTTP_HEADERS_CACHE_SIZE = 15;

    /**
     * The maximum number of headers that are cached per connection. Defaults to 15. If this is set to zero the cache is disabled.
     */
    public static final UndertowOption<Integer> HTTP_HEADERS_CACHE_SIZE = UndertowOption.create("HTTP_HEADERS_CACHE_SIZE", Integer.class);

    /**
     * If the SSLEngine should prefer the servers cipher version. Only applicable on JDK8+.
     */
    public static final UndertowOption<Boolean> SSL_USER_CIPHER_SUITES_ORDER = UndertowOption.create("SSL_USER_CIPHER_SUITES_ORDER", Boolean.class);


    public static final UndertowOption<Boolean> ALLOW_UNESCAPED_CHARACTERS_IN_URL = UndertowOption.create("ALLOW_UNESCAPED_CHARACTERS_IN_URL", Boolean.class);

    /**
     * The server shutdown timeout in milliseconds after which the executor will be forcefully shut down interrupting
     * tasks which are still executing.
     * <p>
     * There is no timeout by default.
     */
    public static final UndertowOption<Integer> SHUTDOWN_TIMEOUT = UndertowOption.create("SHUTDOWN_TIMEOUT", Integer.class);


    /**
     * Enable or disable blocking I/O for a newly created channel thread.
     */
    public static final UndertowOption<Boolean> ALLOW_BLOCKING = UndertowOption.create("ALLOW_BLOCKING", Boolean.class);

    /**
     * Enable multicast support for a socket.  The value type for this option is {@code boolean}.  Note that some
     * implementations may add overhead when multicast sockets are in use.
     */
    public static final UndertowOption<Boolean> MULTICAST = UndertowOption.create("MULTICAST", Boolean.class);

    /**
     * Enable broadcast support for IP datagram sockets.  The value type for this option is {@code boolean}.  If you
     * intend to send datagrams to a broadcast address, this option must be enabled.
     */
    public static final UndertowOption<Boolean> BROADCAST = UndertowOption.create("BROADCAST", Boolean.class);

    /**
     * Configure a TCP socket to send an {@code RST} packet on close.  The value type for this option is {@code boolean}.
     */
    public static final UndertowOption<Boolean> CLOSE_ABORT = UndertowOption.create("CLOSE_ABORT", Boolean.class);

    /**
     * The receive buffer size.  The value type for this option is {@code int}.  This may be used by an XNIO provider
     * directly, or it may be passed to the underlying operating system, depending on the channel type.  Buffer
     * sizes must always be greater than 0.  Note that this value is just a hint; if the application needs to know
     * what value was actually stored for this option, it must call {@code getOption(Options.RECEIVE_BUFFER)} on the
     * channel to verify.  On most operating systems, the receive buffer size may not be changed on a socket after
     * it is connected; in these cases, calling {@code setOption(Options.RECEIVE_BUFFER, val)} will return {@code null}.
     */
    public static final UndertowOption<Integer> RECEIVE_BUFFER = UndertowOption.create("RECEIVE_BUFFER", Integer.class);

    /**
     * Configure an IP socket to reuse addresses.  The value type for this option is {@code boolean}.
     */
    public static final UndertowOption<Boolean> REUSE_ADDRESSES = UndertowOption.create("REUSE_ADDRESSES", Boolean.class);

    /**
     * The send buffer size.  The value type for this option is {@code int}.  This may be used by an XNIO provider
     * directly, or it may be passed to the underlying operating system, depending on the channel type.  Buffer
     * sizes must always be greater than 0.  Note that this value is just a hint; if the application needs to know
     * what value was actually stored for this option, it must call {@code getOption(Options.SEND_BUFFER)} on the
     * channel to verify.
     */
    public static final UndertowOption<Integer> SEND_BUFFER = UndertowOption.create("SEND_BUFFER", Integer.class);

    /**
     * Configure a TCP socket to disable Nagle's algorithm.  The value type for this option is {@code boolean}.
     */
    public static final UndertowOption<Boolean> TCP_NODELAY = UndertowOption.create("TCP_NODELAY", Boolean.class);

    /**
     * Set the multicast time-to-live field for datagram sockets.  The value type for this option is {@code int}.
     */
    public static final UndertowOption<Integer> MULTICAST_TTL = UndertowOption.create("MULTICAST_TTL", Integer.class);

    /**
     * Set the IP traffic class/type-of-service for the channel.  The value type for this option is {@code int}.
     */
    public static final UndertowOption<Integer> IP_TRAFFIC_CLASS = UndertowOption.create("IP_TRAFFIC_CLASS", Integer.class);

    /**
     * Configure a TCP socket to receive out-of-band data alongside regular data.  The value type for this option is
     * {@code boolean}.
     */
    public static final UndertowOption<Boolean> TCP_OOB_INLINE = UndertowOption.create("TCP_OOB_INLINE", Boolean.class);

    /**
     * Configure a channel to send TCP keep-alive messages in an implementation-dependent manner. The value type for
     * this option is {@code boolean}.
     */
    public static final UndertowOption<Boolean> KEEP_ALIVE = UndertowOption.create("KEEP_ALIVE", Boolean.class);

    /**
     * Configure a server with the specified backlog.  The value type for this option is {@code int}.
     */
    public static final UndertowOption<Integer> BACKLOG = UndertowOption.create("BACKLOG", Integer.class);

    /**
     * Configure a read timeout for a socket, in milliseconds.  If the given amount of time elapses without
     * a successful read taking place, the socket's next read will throw a {@link ReadTimeoutException}.
     */
    public static final UndertowOption<Integer> READ_TIMEOUT = UndertowOption.create("READ_TIMEOUT", Integer.class);

    /**
     * Configure a write timeout for a socket, in milliseconds.  If the given amount of time elapses without
     * a successful write taking place, the socket's next write will throw a {@link WriteTimeoutException}.
     */
    public static final UndertowOption<Integer> WRITE_TIMEOUT = UndertowOption.create("WRITE_TIMEOUT", Integer.class);

    /**
     * The maximum inbound message size.
     *
     * @since 2.0
     */
    public static final UndertowOption<Integer> MAX_INBOUND_MESSAGE_SIZE = UndertowOption.create("MAX_INBOUND_MESSAGE_SIZE", Integer.class);

    /**
     * The maximum outbound message size.
     *
     * @since 2.0
     */
    public static final UndertowOption<Integer> MAX_OUTBOUND_MESSAGE_SIZE = UndertowOption.create("MAX_OUTBOUND_MESSAGE_SIZE", Integer.class);

    /**
     * Specify whether SSL should be enabled.  If specified in conjunction with {@link #SSL_STARTTLS} then SSL will not
     * be negotiated until {@link org.xnio.channels.SslChannel#startHandshake()} or
     * {@link SslConnection#startHandshake()}  is called.
     *
     * @since 3.0
     */
    public static final UndertowOption<Boolean> SSL_ENABLED = UndertowOption.create("SSL_ENABLED", Boolean.class);
//
//    /**
//     * Specify the SSL client authentication mode.
//     *
//     * @since 2.0
//     */
//    public static final UndertowOption<SslClientAuthMode> SSL_CLIENT_AUTH_MODE = UndertowOption.create( "SSL_CLIENT_AUTH_MODE", SslClientAuthMode.class);
//
//    /**
//     * Specify the cipher suites for an SSL/TLS session.  If a listed cipher suites is not supported, it is ignored; however, if you
//     * specify a list of cipher suites, none of which are supported, an exception will be thrown.
//     *
//     * @since 2.0
//     */
//    public static final UndertowOption<Sequence<String>> SSL_ENABLED_CIPHER_SUITES = Option.sequence(Options.class, "SSL_ENABLED_CIPHER_SUITES", String.class);
//
//    /**
//     * Get the supported cipher suites for an SSL/TLS session.  This option is generally read-only.
//     *
//     * @since 2.0
//     */
//    public static final UndertowOption<Sequence<String>> SSL_SUPPORTED_CIPHER_SUITES = Option.sequence(Options.class, "SSL_SUPPORTED_CIPHER_SUITES", String.class);
//
//    /**
//     * Specify the enabled protocols for an SSL/TLS session.  If a listed protocol is not supported, it is ignored; however, if you
//     * specify a list of protocols, none of which are supported, an exception will be thrown.
//     *
//     * @since 2.0
//     */
//    public static final UndertowOption<Sequence<String>> SSL_ENABLED_PROTOCOLS = Option.sequence(Options.class, "SSL_ENABLED_PROTOCOLS", String.class);
//
//    /**
//     * Get the supported protocols for an SSL/TLS session.  This option is generally read-only.
//     *
//     * @since 2.0
//     */
//    public static final UndertowOption<Sequence<String>> SSL_SUPPORTED_PROTOCOLS = Option.sequence(Options.class, "SSL_SUPPORTED_PROTOCOLS", String.class);

    /**
     * Specify the requested provider for an SSL/TLS session.
     *
     * @since 2.0
     */
    public static final UndertowOption<String> SSL_PROVIDER = UndertowOption.create("SSL_PROVIDER", String.class);

    /**
     * Specify the protocol name for an SSL context.
     *
     * @since 2.1
     */
    public static final UndertowOption<String> SSL_PROTOCOL = UndertowOption.create("SSL_PROTOCOL", String.class);

    /**
     * Enable or disable session creation for an SSL connection.  Defaults to {@code true} to enable session creation.
     *
     * @since 2.0
     */
    public static final UndertowOption<Boolean> SSL_ENABLE_SESSION_CREATION = UndertowOption.create("SSL_ENABLE_SESSION_CREATION", Boolean.class);

    /**
     * Specify whether SSL conversations should be in client or server mode.  Defaults to {@code false} (use server mode).  If
     * set to {@code true}, the client and server side swap negotiation roles.
     *
     * @since 2.0
     */
    public static final UndertowOption<Boolean> SSL_USE_CLIENT_MODE = UndertowOption.create("SSL_USE_CLIENT_MODE", Boolean.class);

    /**
     * The size of the SSL client session cache.
     *
     * @since 3.0
     */
    public static final UndertowOption<Integer> SSL_CLIENT_SESSION_CACHE_SIZE = UndertowOption.create("SSL_CLIENT_SESSION_CACHE_SIZE", Integer.class);

    /**
     * The SSL client session timeout (in seconds).
     *
     * @since 3.0
     */
    public static final UndertowOption<Integer> SSL_CLIENT_SESSION_TIMEOUT = UndertowOption.create("SSL_CLIENT_SESSION_TIMEOUT", Integer.class);

    /**
     * The size of the SSL server session cache.
     *
     * @since 3.0
     */
    public static final UndertowOption<Integer> SSL_SERVER_SESSION_CACHE_SIZE = UndertowOption.create("SSL_SERVER_SESSION_CACHE_SIZE", Integer.class);

    /**
     * The SSL server session timeout (in seconds).
     *
     * @since 3.0
     */
    public static final UndertowOption<Integer> SSL_SERVER_SESSION_TIMEOUT = UndertowOption.create("SSL_SERVER_SESSION_TIMEOUT", Integer.class);
//
//    /**
//     * The possible key manager classes to use for a JSSE SSL context.
//     *
//     * @since 3.0
//     */
//    public static final UndertowOption<Sequence<Class<? extends KeyManager>>> SSL_JSSE_KEY_MANAGER_CLASSES = Option.typeSequence(Options.class, "SSL_JSSE_KEY_MANAGER_CLASSES", KeyManager.class);
//
//    /**
//     * The possible trust store classes to use for a JSSE SSL context.
//     *
//     * @since 3.0
//     */
//    public static final UndertowOption<Sequence<Class<? extends TrustManager>>> SSL_JSSE_TRUST_MANAGER_CLASSES = Option.typeSequence(Options.class, "SSL_JSSE_TRUST_MANAGER_CLASSES", TrustManager.class);
//
//    /**
//     * The configuration of a secure RNG for SSL usage.
//     *
//     * @since 3.0
//     */
//    public static final UndertowOption<OptionMap> SSL_RNG_OPTIONS = UndertowOption.create( "SSL_RNG_OPTIONS", OptionMap.class);

    /**
     * The packet buffer size for SSL.
     *
     * @since 3.0
     */
    public static final UndertowOption<Integer> SSL_PACKET_BUFFER_SIZE = UndertowOption.create("SSL_PACKET_BUFFER_SIZE", Integer.class);

    /**
     * The application buffer size for SSL.
     *
     * @since 3.0
     */
    public static final UndertowOption<Integer> SSL_APPLICATION_BUFFER_SIZE = UndertowOption.create("SSL_APPLICATION_BUFFER_SIZE", Integer.class);

    /**
     * The size of the allocation region to use for SSL packet buffers.
     *
     * @since 3.0
     */
    public static final UndertowOption<Integer> SSL_PACKET_BUFFER_REGION_SIZE = UndertowOption.create("SSL_PACKET_BUFFER_REGION_SIZE", Integer.class);

    /**
     * The size of the allocation region to use for SSL application buffers.
     *
     * @since 3.0
     */
    public static final UndertowOption<Integer> SSL_APPLICATION_BUFFER_REGION_SIZE = UndertowOption.create("SSL_APPLICATION_BUFFER_REGION_SIZE", Integer.class);

    /**
     * Specify whether to use STARTTLS mode (in which a connection starts clear and switches to TLS on demand).
     *
     * @since 3.0
     */
    public static final UndertowOption<Boolean> SSL_STARTTLS = UndertowOption.create("SSL_STARTTLS", Boolean.class);

    /**
     * Specify the (non-authoritative) name of the peer host to use for the purposes of session reuse, as well as
     * for the use of certain cipher suites (such as Kerberos).  If not given, defaults to the host name of the
     * socket address of the peer.
     */
    public static final UndertowOption<String> SSL_PEER_HOST_NAME = UndertowOption.create("SSL_PEER_HOST_NAME", String.class);

    /**
     * Specify the (non-authoritative) port number of the peer port number to use for the purposes of session reuse, as well as
     * for the use of certain cipher suites.  If not given, defaults to the port number of the socket address of the peer.
     */
    public static final UndertowOption<Integer> SSL_PEER_PORT = UndertowOption.create("SSL_PEER_PORT", Integer.class);

    /**
     * Hint to the SSL engine that the key manager implementation(s) is/are non-blocking, so they can be executed
     * in the I/O thread, possibly improving performance by decreasing latency.
     */
    public static final UndertowOption<Boolean> SSL_NON_BLOCKING_KEY_MANAGER = UndertowOption.create("SSL_NON_BLOCKING_KEY_MANAGER", Boolean.class);

    /**
     * Hint to the SSL engine that the trust manager implementation(s) is/are non-blocking, so they can be executed
     * in the I/O thread, possibly improving performance by decreasing latency.
     */
    public static final UndertowOption<Boolean> SSL_NON_BLOCKING_TRUST_MANAGER = UndertowOption.create("SSL_NON_BLOCKING_TRUST_MANAGER", Boolean.class);

    /**
     * Specify whether direct buffers should be used for socket communications.
     *
     * @since 3.0
     */
    public static final UndertowOption<Boolean> USE_DIRECT_BUFFERS = UndertowOption.create("USE_DIRECT_BUFFERS", Boolean.class);

    /**
     * Determine whether the channel is encrypted, or employs some other level of security.  The interpretation of this flag
     * is specific to the channel in question; however, whatever the channel type, this flag is generally read-only.
     */
    public static final UndertowOption<Boolean> SECURE = UndertowOption.create("SECURE", Boolean.class);

    /**
     * Specify whether SASL mechanisms which implement forward secrecy between sessions are required.
     *
     * @see Sasl#POLICY_FORWARD_SECRECY
     */
    public static final UndertowOption<Boolean> SASL_POLICY_FORWARD_SECRECY = UndertowOption.create("SASL_POLICY_FORWARD_SECRECY", Boolean.class);

    /**
     * Specify whether SASL mechanisms which are susceptible to active (non-dictionary) attacks are permitted.
     *
     * @see Sasl#POLICY_NOACTIVE
     */
    public static final UndertowOption<Boolean> SASL_POLICY_NOACTIVE = UndertowOption.create("SASL_POLICY_NOACTIVE", Boolean.class);

    /**
     * Specify whether SASL mechanisms which accept anonymous logins are permitted.
     *
     * @see Sasl#POLICY_NOANONYMOUS
     */
    public static final UndertowOption<Boolean> SASL_POLICY_NOANONYMOUS = UndertowOption.create("SASL_POLICY_NOANONYMOUS", Boolean.class);

    /**
     * Specify whether SASL mechanisms which are susceptible to passive dictionary attacks are permitted.
     *
     * @see Sasl#POLICY_NODICTIONARY
     */
    public static final UndertowOption<Boolean> SASL_POLICY_NODICTIONARY = UndertowOption.create("SASL_POLICY_NODICTIONARY", Boolean.class);

    /**
     * Specify whether SASL mechanisms which are susceptible to simple plain passive attacks are permitted.
     *
     * @see Sasl#POLICY_NOPLAINTEXT
     */
    public static final UndertowOption<Boolean> SASL_POLICY_NOPLAINTEXT = UndertowOption.create("SASL_POLICY_NOPLAINTEXT", Boolean.class);

    /**
     * Specify whether SASL mechanisms which pass client credentials are required.
     *
     * @see Sasl#POLICY_PASS_CREDENTIALS
     */
    public static final UndertowOption<Boolean> SASL_POLICY_PASS_CREDENTIALS = UndertowOption.create("SASL_POLICY_PASS_CREDENTIALS", Boolean.class);
//
//    /**
//     * Specify the SASL quality-of-protection to use.
//     *
//     * @see Sasl#QOP
//     */
//    public static final UndertowOption<Sequence<SaslQop>> SASL_QOP = Option.sequence(Options.class, "SASL_QOP", SaslQop.class);
//
//    /**
//     * Specify the SASL cipher strength to use.
//     *
//     * @see Sasl#STRENGTH
//     */
//    public static final UndertowOption<SaslStrength> SASL_STRENGTH = UndertowOption.create( "SASL_STRENGTH", SaslStrength.class);

    /**
     * Specify whether the SASL server must authenticate to the client.
     *
     * @see Sasl#SERVER_AUTH
     */
    public static final UndertowOption<Boolean> SASL_SERVER_AUTH = UndertowOption.create("SASL_SERVER_AUTH", Boolean.class);

    /**
     * Specify whether SASL mechanisms should attempt to reuse authenticated session information.
     *
     * @see Sasl#REUSE
     */
    public static final UndertowOption<Boolean> SASL_REUSE = UndertowOption.create("SASL_REUSE", Boolean.class);
//
//    /**
//     * A list of SASL mechanisms, in decreasing order of preference.
//     */
//    public static final UndertowOption<Sequence<String>> SASL_MECHANISMS = Option.sequence(Options.class, "SASL_MECHANISMS", String.class);
//
//    /**
//     * A list of disallowed SASL mechanisms.
//     */
//    public static final UndertowOption<Sequence<String>> SASL_DISALLOWED_MECHANISMS = Option.sequence(Options.class, "SASL_DISALLOWED_MECHANISMS", String.class);
//
//    /**
//     * A list of provider specific SASL properties.
//     */
//    public static final UndertowOption<Sequence<Property>> SASL_PROPERTIES = Option.sequence(Options.class, "SASL_PROPERTIES", Property.class);
//
//    /**
//     * The file access mode to use when opening a file.
//     */
//    public static final UndertowOption<FileAccess> FILE_ACCESS = UndertowOption.create( "FILE_ACCESS", FileAccess.class);

    /**
     * A flag which indicates that opened files should be appended to.  Some platforms do not support both append and
     * {@link FileAccess#READ_WRITE} at the same time.
     */
    public static final UndertowOption<Boolean> FILE_APPEND = UndertowOption.create("FILE_APPEND", Boolean.class);

    /**
     * A flag which indicates that a file should be created if it does not exist ({@code true} by default for writing files,
     * {@code false} by default for reading files).
     */
    public static final UndertowOption<Boolean> FILE_CREATE = UndertowOption.create("FILE_CREATE", Boolean.class);

    /**
     * The stack size (in bytes) to attempt to use for worker threads.
     */
    public static final UndertowOption<Long> STACK_SIZE = UndertowOption.create("STACK_SIZE", Long.class);

    /**
     * The name to use for a newly created worker.  If not specified, the string "XNIO" will be used.  The worker name
     * is used as a part of the thread name for created threads, and for any management constructs.
     */
    public static final UndertowOption<String> WORKER_NAME = UndertowOption.create("WORKER_NAME", String.class);

    /**
     * The thread priority for newly created worker threads.  If not specified, the platform default value will be used.
     */
    public static final UndertowOption<Integer> THREAD_PRIORITY = UndertowOption.create("THREAD_PRIORITY", Integer.class);

    /**
     * Specify whether worker threads should be daemon threads.  Defaults to {@code false}.
     */
    public static final UndertowOption<Boolean> THREAD_DAEMON = UndertowOption.create("THREAD_DAEMON", Boolean.class);

    /**
     * Specify the number of I/O threads to create for the worker.  If not specified, a default will be chosen.
     */
    public static final UndertowOption<Integer> WORKER_IO_THREADS = UndertowOption.create("WORKER_IO_THREADS", Integer.class);

    /**
     * Specify the number of read threads to create for the worker.  If not specified, a default will be chosen.
     *
     * @deprecated Use {@link #WORKER_IO_THREADS} instead.
     */
    @Deprecated
    public static final UndertowOption<Integer> WORKER_READ_THREADS = UndertowOption.create("WORKER_READ_THREADS", Integer.class);

    /**
     * Specify the number of write threads to create for the worker.  If not specified, a default will be chosen.
     *
     * @deprecated Use {@link #WORKER_IO_THREADS} instead.
     */
    @Deprecated
    public static final UndertowOption<Integer> WORKER_WRITE_THREADS = UndertowOption.create("WORKER_WRITE_THREADS", Integer.class);

    /**
     * Specify whether a server, acceptor, or connector should be attached to write threads.  By default, the establishing
     * phase of connections are attached to read threads.  Use this option if the client or server writes a message
     * directly upon connect.
     */
    public static final UndertowOption<Boolean> WORKER_ESTABLISH_WRITING = UndertowOption.create("WORKER_ESTABLISH_WRITING", Boolean.class);

    /**
     * Specify the number of accept threads a single socket server should have.  Specifying more than one can result in spurious wakeups
     * for a socket server under low connection volume, but higher throughput at high connection volume.  The minimum value
     * is 1, and the maximum value is equal to the number of available worker threads.
     */
    @Deprecated
    public static final UndertowOption<Integer> WORKER_ACCEPT_THREADS = UndertowOption.create("WORKER_ACCEPT_THREADS", Integer.class);

    /**
     * Specify the number of "core" threads for the worker task thread pool.
     */
    public static final UndertowOption<Integer> WORKER_TASK_CORE_THREADS = UndertowOption.create("WORKER_TASK_CORE_THREADS", Integer.class);

    /**
     * Specify the maximum number of threads for the worker task thread pool.
     */
    public static final UndertowOption<Integer> WORKER_TASK_MAX_THREADS = UndertowOption.create("WORKER_TASK_MAX_THREADS", Integer.class);

    /**
     * Specify the number of milliseconds to keep non-core task threads alive.
     */
    public static final UndertowOption<Integer> WORKER_TASK_KEEPALIVE = UndertowOption.create("WORKER_TASK_KEEPALIVE", Integer.class);

    /**
     * Specify the maximum number of worker tasks to allow before rejecting.
     */
    public static final UndertowOption<Integer> WORKER_TASK_LIMIT = UndertowOption.create("WORKER_TASK_LIMIT", Integer.class);

    /**
     * Specify that output should be buffered.  The exact behavior of the buffering is not specified; it may flush based
     * on buffered size or time.  An explicit {@link SuspendableWriteChannel#flush()} will still cause
     * the channel to flush its contents immediately.
     */
    public static final UndertowOption<Boolean> CORK = UndertowOption.create("CORK", Boolean.class);

    /**
     * The high water mark for a server's connections.  Once this number of connections have been accepted, accepts
     * will be suspended for that server.
     */
    public static final UndertowOption<Integer> CONNECTION_HIGH_WATER = UndertowOption.create("CONNECTION_HIGH_WATER", Integer.class);

    /**
     * The low water mark for a server's connections.  Once the number of active connections have dropped below this
     * number, accepts can be resumed for that server.
     */
    public static final UndertowOption<Integer> CONNECTION_LOW_WATER = UndertowOption.create("CONNECTION_LOW_WATER", Integer.class);

    /**
     * The compression level to apply for compressing streams and channels.
     */
    public static final UndertowOption<Integer> COMPRESSION_LEVEL = UndertowOption.create("COMPRESSION_LEVEL", Integer.class);
//
//    /**
//     * The compression type to apply for compressing streams and channels.
//     */
//    public static final UndertowOption<CompressionType> COMPRESSION_TYPE = UndertowOption.create( "COMPRESSION_TYPE", CompressionType.class);

    /**
     * The number of balancing tokens, if connection-balancing is enabled.  Must be less than the number of I/O threads,
     * or 0 to disable balancing and just accept opportunistically.
     */
    public static final UndertowOption<Integer> BALANCING_TOKENS = UndertowOption.create("BALANCING_TOKENS", Integer.class);

    /**
     * The number of connections to create per connection-balancing token, if connection-balancing is enabled.
     */
    public static final UndertowOption<Integer> BALANCING_CONNECTIONS = UndertowOption.create("BALANCING_CONNECTIONS", Integer.class);

    /**
     * The poll interval for poll based file system watchers.  Defaults to 5000ms.  Ignored on Java 7 and later.
     */
    public static final UndertowOption<Integer> WATCHER_POLL_INTERVAL = UndertowOption.create("WATCHER_POLL_INTERVAL", Integer.class);

    private UndertowOptions() {

    }
}
