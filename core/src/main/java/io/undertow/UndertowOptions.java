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

import org.xnio.Option;

/**
 * @author Stuart Douglas
 */
public class UndertowOptions {

    /**
     * The maximum size in bytes of a http request header.
     */
    public static final Option<Integer> MAX_HEADER_SIZE = Option.simple(UndertowOptions.class, "MAX_HEADER_SIZE", Integer.class);
    /**
     * The default size we allow for the HTTP header.
     */
    public static final int DEFAULT_MAX_HEADER_SIZE = 1024 * 1024;

    /**
     * The default maximum size of the HTTP entity body.
     */
    public static final Option<Long> MAX_ENTITY_SIZE = Option.simple(UndertowOptions.class, "MAX_ENTITY_SIZE", Long.class);

    /**
     * The default maximum size of the HTTP entity body when using the mutiltipart parser. Generall this will be larger than {@link #MAX_ENTITY_SIZE}.
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
     * for read/write timeouts.
     *
     */
    public static final Option<Integer> IDLE_TIMEOUT = Option.simple(UndertowOptions.class, "IDLE_TIMEOUT", Integer.class);

    /**
     * The maximum allowed time of reading HTTP request in milliseconds.
     *
     * <code>-1</code> or missing value disables this functionality.
     */
    public static final Option<Integer> REQUEST_PARSE_TIMEOUT = Option.simple(UndertowOptions.class, "REQUEST_PARSE_TIMEOUT", Integer.class);

    /**
     * The amount of time the connection can be idle with no current requests before it is closed;
     */
    public static final Option<Integer> NO_REQUEST_TIMEOUT = Option.simple(UndertowOptions.class, "NO_REQUEST_TIMEOUT", Integer.class);

    /**
     * The maximum number of parameters that will be parsed. This is used to protect against hash vulnerabilities.
     * <p>
     * This applies to both query parameters, and to POST data, but is not cumulative (i.e. you can potentially have
     * max parameters * 2 total parameters).
     * <p>
     * Defaults to 1000
     */
    public static final Option<Integer> MAX_PARAMETERS = Option.simple(UndertowOptions.class, "MAX_PARAMETERS", Integer.class);

    /**
     * The maximum number of headers that will be parsed. This is used to protect against hash vulnerabilities.
     * <p>
     * Defaults to 200
     */
    public static final Option<Integer> MAX_HEADERS = Option.simple(UndertowOptions.class, "MAX_HEADERS", Integer.class);


    /**
     * The maximum number of cookies that will be parsed. This is used to protect against hash vulnerabilities.
     * <p>
     * Defaults to 200
     */
    public static final Option<Integer> MAX_COOKIES = Option.simple(UndertowOptions.class, "MAX_COOKIES", Integer.class);

    /**
     * If a request comes in with encoded / characters (i.e. %2F), will these be decoded.
     * <p>
     * This can cause security problems if a front end proxy does not perform the same decoding, and as a result
     * this is disabled by default.
     * <p>
     * Defaults to false
     *
     * See <a href="http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2007-0450">CVE-2007-0450</a>
     */
    public static final Option<Boolean> ALLOW_ENCODED_SLASH = Option.simple(UndertowOptions.class, "ALLOW_ENCODED_SLASH", Boolean.class);

    /**
     * If this is true then the parser will decode the URL and query parameters using the selected character encoding (UTF-8 by default). If this is false they will
     * not be decoded. This will allow a later handler to decode them into whatever charset is desired.
     * <p>
     * Defaults to true.
     */
    public static final Option<Boolean> DECODE_URL = Option.simple(UndertowOptions.class, "DECODE_URL", Boolean.class);


    /**
     * If this is true then the parser will decode the URL and query parameters using the selected character encoding (UTF-8 by default). If this is false they will
     * not be decoded. This will allow a later handler to decode them into whatever charset is desired.
     * <p>
     * Defaults to true.
     */
    public static final Option<String> URL_CHARSET = Option.simple(UndertowOptions.class, "URL_CHARSET", String.class);

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
     * Defaults to 16384.
     */
    public static final Option<Integer> MAX_BUFFERED_REQUEST_SIZE = Option.simple(UndertowOptions.class, "MAX_BUFFERED_REQUEST_SIZE", Integer.class);

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
    @Deprecated
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
     * The maximum number of concurrent
     */
    public static final Option<Integer> HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS", Integer.class);

    public static final Option<Integer> HTTP2_SETTINGS_INITIAL_WINDOW_SIZE = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_INITIAL_WINDOW_SIZE", Integer.class);
    public static final Option<Integer> HTTP2_SETTINGS_MAX_FRAME_SIZE = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_MAX_FRAME_SIZE", Integer.class);
    public static final Option<Integer> HTTP2_SETTINGS_MAX_HEADER_LIST_SIZE = Option.simple(UndertowOptions.class, "HTTP2_SETTINGS_MAX_HEADER_LIST_SIZE", Integer.class);

    /**
     * The maximum amount of padding to send in a HTTP/2 frame. Actual amount will be randomly determined, defaults to Zero.
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
     * If the number of pending requests exceeds this number then requests will be queued, the difference between this and max concurrent streams determins
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
     * The maximum AJP packet size, default is 8192
     */
    public static final Option<Integer> MAX_AJP_PACKET_SIZE = Option.simple(UndertowOptions.class, "MAX_AJP_PACKET_SIZE", Integer.class);

    /**
     * If this is true then HTTP/1.1 requests will be failed if no host header is present.
     */
    public static final Option<Boolean> REQUIRE_HOST_HTTP11 = Option.simple(UndertowOptions.class, "REQUIRE_HOST_HTTP11", Boolean.class);

    private UndertowOptions() {

    }
}
