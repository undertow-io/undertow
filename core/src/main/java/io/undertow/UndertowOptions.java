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
    public static final int DEFAULT_MAX_HEADER_SIZE = 50 * 1024;

    /**
     * The default maximum size of the HTTP entity body.
     */
    public static final Option<Long> MAX_ENTITY_SIZE = Option.simple(UndertowOptions.class, "MAX_ENTITY_SIZE", Long.class);

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
     */
    public static final Option<Long> IDLE_TIMEOUT = Option.simple(UndertowOptions.class, "IDLE_TIMEOUT", Long.class);

    /**
     * The maximum number of parameters that will be parsed. This is used to protect against hash vulnerabilities.
     * <p/>
     * This applies to both query parameters, and to POST data, but is not cumulative (i.e. you can potentially have
     * max parameters * 2 total parameters).
     * <p/>
     * Defaults to 1000
     */
    public static final Option<Integer> MAX_PARAMETERS = Option.simple(UndertowOptions.class, "MAX_PARAMETERS", Integer.class);

    /**
     * The maximum number of headers that will be parsed. This is used to protect against hash vulnerabilities.
     * <p/>
     * Defaults to 200
     */
    public static final Option<Integer> MAX_HEADERS = Option.simple(UndertowOptions.class, "MAX_HEADERS", Integer.class);


    /**
     * The maximum number of cookies that will be parsed. This is used to protect against hash vulnerabilities.
     * <p/>
     * Defaults to 200
     */
    public static final Option<Integer> MAX_COOKIES = Option.simple(UndertowOptions.class, "MAX_COOKIES", Integer.class);

    /**
     * If a request comes in with encoded / characters (i.e. %2F), will these be decoded.
     * <p/>
     * This can cause security problems if a front end proxy does not perform the same decoding, and as a result
     * this is disabled by default.
     * <p/>
     * Defaults to false
     *
     * @see http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2007-0450
     */
    public static final Option<Boolean> ALLOW_ENCODED_SLASH = Option.simple(UndertowOptions.class, "ALLOW_ENCODED_SLASH", Boolean.class);

    /**
     * If this is true then the parser will decode the URL and query parameters using the selected character encoding (UTF-8 by default). If this is false they will
     * not be decoded. This will allow a later handler to decode them into whatever charset is desired.
     * <p/>
     * Defaults to true.
     */
    public static final Option<Boolean> DECODE_URL = Option.simple(UndertowOptions.class, "DECODE_URL", Boolean.class);


    /**
     * If this is true then the parser will decode the URL and query parameters using the selected character encoding (UTF-8 by default). If this is false they will
     * not be decoded. This will allow a later handler to decode them into whatever charset is desired.
     * <p/>
     * Defaults to true.
     */
    public static final Option<String> URL_CHARSET = Option.simple(UndertowOptions.class, "URL_CHARSET", String.class);

    /**
     * If this is true then a Connection: keep-alive header will be added to responses, even when it is not strictly required by
     * the specification.
     * <p/>
     * Defaults to true
     */
    public static final Option<Boolean> ALWAYS_SET_KEEP_ALIVE = Option.simple(UndertowOptions.class, "ALWAYS_SET_KEEP_ALIVE", Boolean.class);

    /**
     * If this is true then a Date header will be added to all responses. The HTTP spec says this header should be added to all
     * responses, unless the server does not have an accurate clock.
     * <p/>
     * Defaults to true
     */
    public static final Option<Boolean> ALWAYS_SET_DATE = Option.simple(UndertowOptions.class, "ALWAYS_SET_DATE", Boolean.class);

    /**
     * Maximum size of a buffered request, in bytes
     * <p/>
     * Requests are not usually buffered, the most common case is when performing SSL renegotiation for a POST request, and the post data must be fully
     * buffered in order to perform the renegotiation.
     * <p/>
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
     * <p/>
     * Unquoted cookie values may not contain equals characters. If present the value ends before the equals sign. The remainder of the cookie value will be dropped.
     * <p/>
     * default is false
     */
    public static final Option<Boolean> ALLOW_EQUALS_IN_COOKIE_VALUE = Option.simple(UndertowOptions.class, "ALLOW_EQUALS_IN_COOKIE_VALUE", Boolean.class);

    /**
     * If we should attempt to use SPDY for HTTPS connections.
     */
    public static final Option<Boolean> ENABLE_SPDY = Option.simple(UndertowOptions.class, "ENABLE_SPDY", Boolean.class);

    private UndertowOptions() {

    }
}
