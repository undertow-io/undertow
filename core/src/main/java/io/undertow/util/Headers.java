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

package io.undertow.util;

/**
 * NOTE: if you add a new header here you must also add it to {@link io.undertow.server.httpparser.HttpParser}
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Headers {

    private Headers() {
    }

    // Header names


    public static final String ACCEPT = "accept";
    public static final String ACCEPT_CHARSET = "accept-charset";
    public static final String ACCEPT_ENCODING = "accept-encoding";
    public static final String ACCEPT_LANGUAGE = "accept-language";
    public static final String ACCEPT_RANGES = "accept-ranges";
    public static final String AGE = "age";
    public static final String ALLOW = "allow";
    public static final String AUTHORIZATION = "authorization";
    public static final String CACHE_CONTROL = "cache-control";
    public static final String COOKIE = "cookie";
    public static final String COOKIE2 = "cookie2";
    public static final String CONNECTION = "connection";
    public static final String CONTENT_DISPOSITION = "content-disposition";
    public static final String CONTENT_ENCODING = "content-encoding";
    public static final String CONTENT_LANGUAGE = "content-language";
    public static final String CONTENT_LENGTH = "content-length";
    public static final String CONTENT_LOCATION = "content-location";
    public static final String CONTENT_MD5 = "content-md5";
    public static final String CONTENT_RANGE = "content-range";
    public static final String CONTENT_TYPE = "content-type";
    public static final String DATE = "date";
    public static final String ETAG = "etag";
    public static final String EXPECT = "expect";
    public static final String EXPIRES = "expires";
    public static final String FROM = "from";
    public static final String HOST = "host";
    public static final String IF_MATCH = "if-match";
    public static final String IF_MODIFIED_SINCE = "if-modified-since";
    public static final String IF_NONE_MATCH = "if-none-match";
    public static final String IF_RANGE = "if-range";
    public static final String IF_UNMODIFIED_SINCE = "if-unmodified-since";
    public static final String LAST_MODIFIED = "last-modified";
    public static final String LOCATION = "location";
    public static final String MAX_FORWARDS = "max-forwards";
    public static final String ORIGIN = "origin";
    public static final String PRAGMA = "pragma";
    public static final String PROXY_AUTHENTICATE = "proxy-authenticate";
    public static final String PROXY_AUTHORIZATION = "proxy-authorization";
    public static final String RANGE = "range";
    public static final String REFERER = "referer";
    public static final String REFRESH = "refresh";
    public static final String RETRY_AFTER = "retry-after";
    public static final String SERVER = "server";
    public static final String SET_COOKIE = "set-cookie";
    public static final String SET_COOKIE2 = "set-cookie2";
    public static final String STRICT_TRANSPORT_SECURITY = "strict-transport-security";
    public static final String TE = "te";
    public static final String TRAILER = "trailer";
    public static final String TRANSFER_ENCODING = "transfer-encoding";
    public static final String UPGRADE = "upgrade";
    public static final String USER_AGENT = "user-agent";
    public static final String VARY = "vary";
    public static final String VIA = "via";
    public static final String WARNING = "warning";
    public static final String WWW_AUTHENTICATE = "www-authenticate";

    // Content codings

    public static final String COMPRESS = "compress";
    public static final String X_COMPRESS = "x-compress";
    public static final String DEFLATE = "deflate";
    public static final String IDENTITY = "identity";
    public static final String GZIP = "gzip";
    public static final String X_GZIP = "x-gzip";

    // Transfer codings

    public static final String CHUNKED = "chunked";
    // IDENTITY
    // GZIP
    // COMPRESS
    // DEFLATE

    // Connection values
    public static final String KEEP_ALIVE = "keep-alive";
    public static final String CLOSE = "close";

}
