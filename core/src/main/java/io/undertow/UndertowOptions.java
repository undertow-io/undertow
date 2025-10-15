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

package io.undertow;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.xnio.Option;
import org.xnio.Options;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.WriteTimeoutException;

/**
 * @author Stuart Douglas
 */
public class UndertowOptions {
    /**
     *  The default read timeout to be used by read operations that absolutely require a timeout. Used only when both
     *   READ_TIMEOUT and IDLE_TIMEOUT are not used. <br>UNIT: milliseconds.
     */
    public static final int DEFAULT_READ_TIMEOUT = 600000;
    /**
     * The maximum size in bytes of a http request header. <br>UNIT: Bytes.
     */
    public static final Option<Integer> MAX_HEADER_SIZE = Option.simple(UndertowOptions.class, "MAX_HEADER_SIZE", Integer.class);
    /**
     * The default size we allow for the HTTP header. <br>UNIT: Bytes.
     */
    public static final int DEFAULT_MAX_HEADER_SIZE = 1024 * 1024;

    /**
     * The default maximum size of the HTTP entity body. <br>UNIT: Bytes.
     */
    public static final Option<Long> MAX_ENTITY_SIZE = Option.simple(UndertowOptions.class, "MAX_ENTITY_SIZE", Long.class);

    /**
     * The default maximum size of the HTTP entity body when using the multitipart parser. Generally this will be larger than {@link #MAX_ENTITY_SIZE}.
     *
     * If this is not specified it will be the same as {@link #MAX_ENTITY_SIZE}.
     */
    public static final Option<Long> MULTIPART_MAX_ENTITY_SIZE = Option.simple(UndertowOptions.class, "MULTIPART_MAX_ENTITY_SIZE", Long.class);

    /**
     * We do not have a default upload limit
     */
    public static final long DEFAULT_MAX_ENTITY_SIZE = -1;

    /**
     * If we should buffer pipelined requests. Defaults to false.
     */
    public static final Option<Boolean> BUFFER_PIPELINED_DATA = Option.simple(UndertowOptions.class, "BUFFER_PIPELINED_DATA", Boolean.class);

    /**
     * The idle timeout in milliseconds after which the channel will be closed.
     *
     * If the underlying channel already has a read or write timeout set the smaller of the two values will be used
     * for read/write timeouts. <br>UNIT: Milliseconds.
     *
     */
    public static final Option<Integer> IDLE_TIMEOUT = Option.simple(UndertowOptions.class, "IDLE_TIMEOUT", Integer.class);

    /**
     * The maximum allowed time of reading HTTP request in milliseconds.
     *
     * <code>-1</code> or missing value disables this functionality. <br>UNIT: Milliseconds.
     */
    public static final Option<Integer> REQUEST_PARSE_TIMEOUT = Option.simple(UndertowOptions.class, "REQUEST_PARSE_TIMEOUT", Integer.class);

    /**
     * The amount of time the connection can be idle with no current requests before it is closed; <br>UNIT: Milliseconds.
     */
    public static final Option<Integer> NO_REQUEST_TIMEOUT = Option.simple(UndertowOptions.class, "NO_REQUEST_TIMEOUT", Integer.class);

    public static final int DEFAULT_MAX_PARAMETERS = 1000;

    /**
     * The maximum number of parameters that will be parsed. This is used to protect against hash vulnerabilities.
     * <p>
     * This applies to both query parameters, and to POST data, but is not cumulative (i.e. you can potentially have
     * max parameters * 2 total parameters).
     * <p>
     * Defaults to 1000
     */
    public static final Option<Integer> MAX_PARAMETERS = Option.simple(UndertowOptions.class, "MAX_PARAMETERS", Integer.class);

    public static final int DEFAULT_MAX_HEADERS = 200;

    /**
     * The maximum number of headers that will be parsed. This is used to protect against hash vulnerabilities.
     * <p>
     * Defaults to 200
     */
    public static final Option<Integer> MAX_HEADERS = Option.simple(UndertowOptions.class, "MAX_HEADERS", Integer.class);


    public static final int DEFAULT_MAX_COOKIES = 200;

    /**
     * The maximum number of cookies that will be parsed. This is used to protect against hash vulnerabilities.
     * <p>
     * Defaults to 200
     */
    public static final Option<Integer> MAX_COOKIES = Option.simple(UndertowOptions.class, "MAX_COOKIES", Integer.class);

    /**
     * Default value of {@link #ALLOW_ENCODED_SLASH} option.
     */
    public static final Boolean DEFAULT_ALLOW_ENCODED_SLASH = Boolean.FALSE;

    /**
     * If a request comes in with encoded / characters (i.e. %2F), will these be decoded.
     * <p>
     * This can cause security problems if a front end proxy does not perform the same decoding, and as a result
     * this is disabled by default.
     * <p>
     * Defaults to {@link #DEFAULT_ALLOW_ENCODED_SLASH}
     * <p>
     * See <a href="http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2007-0450">CVE-2007-0450</a>
     * @deprecated - this option was interpreted improperly.
     * @See {@link #DECODE_SLASH}
     */
    public static final Option<Boolean> ALLOW_ENCODED_SLASH = Option.simple(UndertowOptions.class, "ALLOW_ENCODED_SLASH", Boolean.class);

    /**
     * If a request comes in with encoded / characters (i.e. %2F), will these be decoded.
     * <p>
     * This can cause security problems if a front end proxy does not perform the same decoding, and as a result
     * this is disabled by default.
     * <p>
     * If this option is set explicitly, the {@link #ALLOW_ENCODED_SLASH} is ignored. Should default to <b>true</b>
     * <p>
     * See <a href="http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2007-0450">CVE-2007-0450</a>
     */
    public static final Option<Boolean> DECODE_SLASH = Option.simple(UndertowOptions.class, "DECODE_SLASH", Boolean.class);

    /**
     * If this is true then the parser will decode the URL and query parameters using the selected character encoding (UTF-8 by default). If this is false they will
     * not be decoded. This will allow a later handler to decode them into whatever charset is desired.
     * <p>
     * Defaults to true.
     */
    public static final Option<Boolean> DECODE_URL = Option.simple(UndertowOptions.class, "DECODE_URL", Boolean.class);

    /**
     * The character encoding to be used for the URL decoding of requests.
     * <p>
     * Defaults to {@link #DEFAULT_URL_CHARSET}.
     */
    public static final Option<String> URL_CHARSET = Option.simple(UndertowOptions.class, "URL_CHARSET", String.class);

    /**
     * Default value of {@link #URL_CHARSET} option.
     */
    public static final String DEFAULT_URL_CHARSET = UTF_8.name();

    /**
     * If this is true then a Connection: keep-alive header will be added to responses, even when it is not strictly required by
     * the specification.
     * <p>
     * Defaults to true
     */
    public static final Option<Boolean> ALWAYS_SET_KEEP_ALIVE = Option.simple(UndertowOptions.class, "ALWAYS_SET_KEEP_ALIVE", Boolean.class);

    /**
     * If this is true then a Date header will be added to all responses. The HTTP spec says this header should be added to all
     * responses, unless the server does not have an accurate clock.
     * <p>
     * Defaults to true
     */
    public static final Option<Boolean> ALWAYS_SET_DATE = Option.simple(UndertowOptions.class, "ALWAYS_SET_DATE", Boolean.class);

    /**
     * Maximum size of a buffered request, in bytes
     * <p>
     * Requests are not usually buffered, the most common case is when performing SSL renegotiation for a POST request, and the post data must be fully
     * buffered in order to perform the renegotiation.
     * <p>
     * Defaults to 16384. <br>UNIT: Bytes.
     */
    public static final Option<Integer> MAX_BUFFERED_REQUEST_SIZE = Option.simple(UndertowOptions.class, "MAX_BUFFERED_REQUEST_SIZE", Integer.class);

    /**
     * Default value of request buffer. <br>UNIT: Bytes.
     */
    public static final int DEFAULT_MAX_BUFFERED_REQUEST_SIZE = 16384;

    /**
     * If this is true then Undertow will record the request start time, to allow for request time to be logged
     *
     * This has a small but measurable performance impact
     *
     * default is false
     */
    public static final Option<Boolean> RECORD_REQUEST_START_TIME = Option.simple(UndertowOptions.class, "RECORD_REQUEST_START_TIME", Boolean.class);

    /**
     * If this is true then Undertow will allow non-escaped equals characters in unquoted cookie values.
     * <p>
     * Unquoted cookie values may not contain equals characters. If present the value ends before the equals sign. The remainder of the cookie value will be dropped.
     * <p>
     * default is false
     */
    public static final Option<Boolean> ALLOW_EQUALS_IN_COOKIE_VALUE = Option.simple(UndertowOptions.class, "ALLOW_EQUALS_IN_COOKIE_VALUE", Boolean.class);

    /**
     * If this is true then Undertow will disable RFC6265 compliant cookie parsing for Set-Cookie header instead of legacy backward compatible behavior.
     * <p>
     * default is {@code false}
     * </p>
     */
    public static final Option<Boolean> DISABLE_RFC6265_COOKIE_PARSING = Option.simple(UndertowOptions.class, "DISABLE_RFC6265_COOKIE_PARSING", Boolean.class);

    public static final boolean DEFAULT_DISABLE_RFC6265_COOKIE_PARSING = false;

    /**
     * If this is true then Undertow will enable RFC6265 compliant cookie validation for Set-Cookie header instead of legacy backward compatible behavior.
     *
     * default is false
     */
    public static final Option<Boolean> ENABLE_RFC6265_COOKIE_VALIDATION = Option.simple(UndertowOptions.class, "ENABLE_RFC6265_COOKIE_VALIDATION", Boolean.class);

    // As of Jakarta 6.1, RFC 6265 is used for the cookie specification https://github.com/jakartaee/servlet/issues/37
    public static final boolean DEFAULT_ENABLE_RFC6265_COOKIE_VALIDATION = true;

    /**
     * If we should attempt to use SPDY for HTTPS connections.
     *
     * SPDY is no longer supported, use HTTP/2 instead
     */
    @Deprecated
    public static final Option<Boolean> ENABLE_SPDY = Option.simple(UndertowOptions.class, "ENABLE_SPDY", Boolean.class);

    /**
     * If we should attempt to use HTTP2 for HTTPS connections.
     */
    public static final Option<Boolean> ENABLE_HTTP2 = Option.simple(UndertowOptions.class, "ENABLE_HTTP2", Boolean.class);

    /**
     * If connector level statistics should be enabled. This has a slight performance impact, but allows statistics such
     * as bytes sent/recevied to be monitored.
     *
     * If this is passed to the client then client statistics will be enabled.
     *
     */
    public static final Option<Boolean> ENABLE_STATISTICS = Option.simple(UndertowOptions.class, "ENABLE_STATISTICS", Boolean.class);


    /**
     * If connector level statistics should be enabled. This has a slight performance impact, but allows statistics such
     * as bytes sent/recevied to be monitored.
     */
    @Deprecated(forRemoval = true)
    public static final Option<Boolean> ENABLE_CONNECTOR_STATISTICS = ENABLE_STATISTICS;


    /**
     * If unknown protocols should be allowed. The known protocols are:
     *
     * HTTP/0.9
     * HTTP/1.0
     * HTTP/1.1
     * HTTP/2.0
     *
     * If this is false then requests that specify any other protocol will be rejected with a 400
     *
     * Defaults to false
     */
    public static final Option<Boolean> ALLOW_UNKNOWN_PROTOCOLS = Option.simple(UndertowOptions.class, "ALLOW_UNKNOWN_PROTOCOLS", Boolean.class);

    /**
     * The size of the header table that is used in the encoder
     */
    public static final Option<Integer> HTTP2_SETTINGS_HEADER_TABLE_SIZE = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_HEADER_TABLE_SIZE", Integer.class);
    public static final int HTTP2_SETTINGS_HEADER_TABLE_SIZE_DEFAULT = 4096;

    /**
     * If push should be enabled for this connection.
     */
    public static final Option<Boolean> HTTP2_SETTINGS_ENABLE_PUSH = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_ENABLE_PUSH", Boolean.class);

    /**
     * The maximum number of concurrent http2 streams.
     */
    public static final Option<Integer> HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS", Integer.class);

    public static final int DEFAULT_HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS = -1;

    /**
     * Size of initial HTTP2 window.
     *  <br>UNIT: Bytes.
     */
    public static final Option<Integer> HTTP2_SETTINGS_INITIAL_WINDOW_SIZE = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_INITIAL_WINDOW_SIZE", Integer.class);
    /**
     * Default value of {@link #HTTP2_SETTINGS_INITIAL_WINDOW_SIZE}. <br>UNIT: Bytes.
     */
    public static final int DEFAULT_HTTP2_SETTINGS_INITIAL_WINDOW_SIZE = 65535;
    /**
     * Max frame size for HTTP2.
     *  <br>UNIT: Bytes.
     */
    public static final Option<Integer> HTTP2_SETTINGS_MAX_FRAME_SIZE = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_MAX_FRAME_SIZE", Integer.class);

    /**
     * Deprecated, as it is effectively a duplicate of MAX_HEADER_SIZE
     *
     * @see #MAX_HEADER_SIZE
     */
    @Deprecated(forRemoval = true)
    public static final Option<Integer> HTTP2_SETTINGS_MAX_HEADER_LIST_SIZE = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_MAX_HEADER_LIST_SIZE", Integer.class);

    /**
     * The maximum amount of padding to send in a HTTP/2 frame. Actual amount will be randomly determined, defaults to Zero. <br>UNIT: Bytes.
     */
    public static final Option<Integer> HTTP2_PADDING_SIZE = Option.simple(UndertowOptions.class, "HTTP2_PADDING_SIZE", Integer.class);

    /**
     * Undertow keeps a LRU cache of common huffman encodings. This sets the maximum size, setting this to 0 will disable the caching.
     *
     */
    public static final Option<Integer> HTTP2_HUFFMAN_CACHE_SIZE = Option.simple(UndertowOptions.class, "HTTP2_HUFFMAN_CACHE_SIZE", Integer.class);

    /**
     * The maximum number of concurrent requests that will be processed at a time. This differs from max concurrent streams in that it is not sent to the remote client.
     *
     * If the number of pending requests exceeds this number then requests will be queued, the difference between this and max concurrent streams determines
     * the maximum number of requests that will be queued.
     *
     * Queued requests are processed by a priority queue, rather than a FIFO based queue, using HTTP2 stream priority.
     *
     * If this number is smaller than or equal to zero then max concurrent streams determines the maximum number of streams that can be run.
     *
     *
     */
    public static final Option<Integer> MAX_CONCURRENT_REQUESTS_PER_CONNECTION = Option.simple(UndertowOptions.class, "MAX_CONCURRENT_REQUESTS_PER_CONNECTION", Integer.class);

    /**
     * The maximum number of buffers that will be used before reads are paused in framed protocols. Defaults to 10
     */
    public static final Option<Integer> MAX_QUEUED_READ_BUFFERS = Option.simple(UndertowOptions.class, "MAX_QUEUED_READ_BUFFERS", Integer.class);

    /**
     * The maximum AJP packet size, default is 8192.<br>UNIT: Bytes.
     */
    public static final Option<Integer> MAX_AJP_PACKET_SIZE = Option.simple(UndertowOptions.class, "MAX_AJP_PACKET_SIZE", Integer.class);

    /**
     * If this is true then HTTP/1.1 requests will be failed if no host header is present.
     */
    public static final Option<Boolean> REQUIRE_HOST_HTTP11 = Option.simple(UndertowOptions.class, "REQUIRE_HOST_HTTP11", Boolean.class);

    public static final int DEFAULT_MAX_CACHED_HEADER_SIZE = 150;

    /**
     * The maximum size of a header name+value combo that is cached in the per connection cache. Defaults to 150
     */
    public static final Option<Integer> MAX_CACHED_HEADER_SIZE = Option.simple(UndertowOptions.class, "MAX_CACHED_HEADER_SIZE", Integer.class);

    public static final int DEFAULT_HTTP_HEADERS_CACHE_SIZE = 15;

    /**
     * The maximum number of headers that are cached per connection. Defaults to 15. If this is set to zero the cache is disabled.
     */
    public static final Option<Integer> HTTP_HEADERS_CACHE_SIZE = Option.simple(UndertowOptions.class, "HTTP_HEADERS_CACHE_SIZE", Integer.class);

    /**
     * If the SSLEngine should prefer the servers cipher version. Only applicable on JDK8+.
     */
    public static final Option<Boolean> SSL_USER_CIPHER_SUITES_ORDER = Option.simple(UndertowOptions.class, "SSL_USER_CIPHER_SUITES_ORDER", Boolean.class);

    /**
     * This option forces {@link io.undertow.protocols.ssl.UndertowXnioSsl} to use a specific
     * name as the {@link javax.net.ssl.SNIHostName} for a client connection. If the option is
     * not set, the URL hostname is used when available (for URLs that do not use the IP address).
     * The option can be useful when connecting via IP but want to set a SNI hostname value.
     */
    public static final Option<String> SSL_SNI_HOSTNAME = Option.simple(UndertowOptions.class, "SSL_SNI_HOSTNAME", String.class);

    public static final Option<Boolean> ALLOW_UNESCAPED_CHARACTERS_IN_URL = Option.simple(UndertowOptions.class,"ALLOW_UNESCAPED_CHARACTERS_IN_URL", Boolean.class);

    /**
     * The server shutdown timeout in milliseconds after which the executor will be forcefully shut down interrupting
     * tasks which are still executing.
     *
     * There is no timeout by default.<br>UNIT: Milliseconds.
     */
    public static final Option<Integer> SHUTDOWN_TIMEOUT = Option.simple(UndertowOptions.class, "SHUTDOWN_TIMEOUT", Integer.class);

    /**
     * The endpoint identification algorithm, the empty string can be used to set none.
     *
     * @see javax.net.ssl.SSLParameters#setEndpointIdentificationAlgorithm(String)
     */
    public static final Option<String> ENDPOINT_IDENTIFICATION_ALGORITHM = Option.simple(UndertowOptions.class, "ENDPOINT_IDENTIFICATION_ALGORITHM", String.class);

    /**
     * The maximum numbers of frames that can be queued before reads are suspended. Once this number is hit then reads will not be resumed until {@link #QUEUED_FRAMES_LOW_WATER_MARK}
     * is hit.
     *
     * Defaults to 50
     */
    public static final Option<Integer> QUEUED_FRAMES_HIGH_WATER_MARK = Option.simple(UndertowOptions.class, "QUEUED_FRAMES_HIGH_WATER_MARK", Integer.class);

    /**
     * The point at which reads will resume again after hitting the high water mark
     *
     * Defaults to 10
     */
    public static final Option<Integer> QUEUED_FRAMES_LOW_WATER_MARK = Option.simple(UndertowOptions.class, "QUEUED_FRAMES_LOW_WATER_MARK", Integer.class);

    /**
     * The AJP protocol itself supports the passing of arbitrary request attributes.
     * The reverse proxy passes various information to the AJP connector using request attributes through AJP protocol.
     * Unrecognised request attributes will be ignored unless the entire attribute name matches this regular expression.
     *
     * If not specified, the default value is null.
     */

    public static final Option<Integer> DEFAULT_QUEUED_FRAMES_LOW_WATER_MARK = null;

    public static final Option<String> AJP_ALLOWED_REQUEST_ATTRIBUTES_PATTERN = Option.simple(UndertowOptions.class, "AJP_ALLOWED_REQUEST_ATTRIBUTES_PATTERN", String.class);

    /**
     * If active request tracking should be enabled. This has a slight performance impact, and will only be honored if
     * ENABLE_STATISTICS is also enabled.
     *
     */
    public static final Option<Boolean> TRACK_ACTIVE_REQUESTS = Option.simple(UndertowOptions.class, "TRACK_ACTIVE_REQUESTS", Boolean.class);

    /**
     * Default value of {@link #RST_FRAMES_TIME_WINDOW} option. <br>UNIT: Milliseconds.
     */
    public static final int DEFAULT_RST_FRAMES_TIME_WINDOW = 30000;
    /**
     * Default value of {@link #MAX_RST_FRAMES_PER_WINDOW} option.
     */
    public static final int DEFAULT_MAX_RST_FRAMES_PER_WINDOW = 200;

    /**
     * Window of time per which the number of HTTP2 RST received frames is measured, in milliseconds.
     * If a number of RST frames bigger than {@link #MAX_RST_FRAMES_PER_WINDOW} is received during this time window,
     * the server will send a GO_AWAY frame with error code 11 ({@code ENHANCE_YOUR_CALM}) and it will close the connection.<br>UNIT: Milliseconds.
     */
    public static final Option<Integer> RST_FRAMES_TIME_WINDOW = Option.simple(UndertowOptions.class, "MAX_RST_STREAM_TIME_WINDOW", Integer.class);

    /**
     * Maximum number of HTTP2 RST frames received allowed during a time window.
     * If a number of RST frames bigger than this limit is received during {@link #RST_FRAMES_TIME_WINDOW} milliseconds,
     * the server will send a GO_AWAY frame with error code 11 ({@code ENHANCE_YOUR_CALM}) and it will close the connection.
     */
    public static final Option<Integer> MAX_RST_FRAMES_PER_WINDOW = Option.simple(UndertowOptions.class, "MAX_RST_STREAMS_PER_TIME_WINDOW", Integer.class);

    /**
     * Proposed default minimum size for storing content in memory before persisting to disk.
     * The default value for {@code MEMORY_STORAGE_THRESHOLD} is 16 KB (16384 bytes).
     */
    public static final long DEFAULT_MEMORY_STORAGE_THRESHOLD = 0x4000;

    /**
     * The minimum size in bytes for storing content in memory before persisting to disk. If the file content exceeds
     * the specified <i>file size threshold</i> and the <i>filename</i> is not specified in the form, the content will be stored
     * in memory as long as its size is less than or equal to this minimum size, after which it will be persisted to disk.
     */
    public static final Option<Long> MEMORY_STORAGE_THRESHOLD = Option.simple(UndertowOptions.class, "MEMORY_STORAGE_THRESHOLD", Long.class);


    /**
     * Configure a read timeout for a web socket, in milliseconds. If its present it will override {@link org.xnio.Options#READ_TIMEOUT}. If the given amount of time elapses without
     * a successful read taking place, the socket's next read will throw a {@link ReadTimeoutException}.
     */
    public static final Option<Integer> WEB_SOCKETS_READ_TIMEOUT = Option.simple(Options.class, "WEB_SOCKETS_READ_TIMEOUT", Integer.class);

    /**
     * Configure a write timeout for a web socket, in milliseconds. If its present it will override {@link org.xnio.Options#WRITE_TIMEOUT}. If the given amount of time elapses without
     * a successful write taking place, the socket's next write will throw a {@link WriteTimeoutException}.
     */
    public static final Option<Integer> WEB_SOCKETS_WRITE_TIMEOUT = Option.simple(Options.class, "WEB_SOCKETS_WRITE_TIMEOUT", Integer.class);

    private UndertowOptions() {

    }
}
