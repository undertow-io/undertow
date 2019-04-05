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

package io.undertow.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpHeaderNames {

    private HttpHeaderNames() {
    }

    // Headers as strings

    public static final String ACCEPT = "Accept";
    public static final String ACCEPT_CHARSET = "Accept-Charset";
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String ACCEPT_LANGUAGE = "Accept-Language";
    public static final String ACCEPT_RANGES = "Accept-Ranges";
    public static final String AGE = "Age";
    public static final String ALLOW = "Allow";
    public static final String AUTHENTICATION_INFO = "Authentication-Info";
    public static final String AUTHORIZATION = "Authorization";
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String COOKIE = "Cookie";
    public static final String COOKIE2 = "Cookie2";
    public static final String CONNECTION = "Connection";
    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String CONTENT_LANGUAGE = "Content-Language";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_LOCATION = "Content-Location";
    public static final String CONTENT_MD5 = "Content-MD5";
    public static final String CONTENT_RANGE = "Content-Range";
    public static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String DATE = "Date";
    public static final String ETAG = "ETag";
    public static final String EXPECT = "Expect";
    public static final String EXPIRES = "Expires";
    public static final String FORWARDED = "Forwarded";
    public static final String FROM = "From";
    public static final String HOST = "Host";
    public static final String IF_MATCH = "If-Match";
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String IF_NONE_MATCH = "If-None-Match";
    public static final String IF_RANGE = "If-Range";
    public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
    public static final String LAST_MODIFIED = "Last-Modified";
    public static final String LOCATION = "Location";
    public static final String MAX_FORWARDS = "Max-Forwards";
    public static final String ORIGIN = "Origin";
    public static final String PRAGMA = "Pragma";
    public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    public static final String RANGE = "Range";
    public static final String REFERER = "Referer";
    public static final String REFERRER_POLICY = "Referrer-Policy";
    public static final String REFRESH = "Refresh";
    public static final String RETRY_AFTER = "Retry-After";
    public static final String SEC_WEB_SOCKET_ACCEPT = "Sec-WebSocket-Accept";
    public static final String SEC_WEB_SOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";
    public static final String SEC_WEB_SOCKET_KEY = "Sec-WebSocket-Key";
    public static final String SEC_WEB_SOCKET_KEY1 = "Sec-WebSocket-Key1";
    public static final String SEC_WEB_SOCKET_KEY2 = "Sec-WebSocket-Key2";
    public static final String SEC_WEB_SOCKET_LOCATION = "Sec-WebSocket-Location";
    public static final String SEC_WEB_SOCKET_ORIGIN = "Sec-WebSocket-Origin";
    public static final String SEC_WEB_SOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    public static final String SEC_WEB_SOCKET_VERSION = "Sec-WebSocket-Version";
    public static final String SERVER = "Server";
    public static final String SERVLET_ENGINE = "Servlet-Engine";
    public static final String SET_COOKIE = "Set-Cookie";
    public static final String SET_COOKIE2 = "Set-Cookie2";
    public static final String SSL_CLIENT_CERT = "SSL_CLIENT_CERT";
    public static final String SSL_CIPHER = "SSL_CIPHER";
    public static final String SSL_SESSION_ID = "SSL_SESSION_ID";
    public static final String SSL_CIPHER_USEKEYSIZE = "SSL_CIPHER_USEKEYSIZE";
    public static final String STATUS = "Status";
    public static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    public static final String TE = "TE";
    public static final String TRAILER = "Trailer";
    public static final String TRANSFER_ENCODING = "Transfer-Encoding";
    public static final String UPGRADE = "Upgrade";
    public static final String USER_AGENT = "User-Agent";
    public static final String VARY = "Vary";
    public static final String VIA = "Via";
    public static final String WARNING = "Warning";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String X_DISABLE_PUSH = "X-Disable-Push";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    public static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    public static final String X_FORWARDED_PORT = "X-Forwarded-Port";
    public static final String X_FORWARDED_SERVER = "X-Forwarded-Server";
    public static final String X_FRAME_OPTIONS = "X-Frame-Options";
    public static final String X_XSS_PROTECTION = "X-Xss-Protection";

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

    //MIME header used in multipart file uploads
    public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";

    // Authentication Schemes
    public static final String BASIC = "Basic";
    public static final String DIGEST = "Digest";
    public static final String NEGOTIATE = "Negotiate";

    // Digest authentication Token Names
    public static final String ALGORITHM = "algorithm";
    public static final String AUTH_PARAM = "auth-param";
    public static final String CNONCE = "cnonce";
    public static final String DOMAIN = "domain";
    public static final String NEXT_NONCE = "nextnonce";
    public static final String NONCE = "nonce";
    public static final String NONCE_COUNT = "nc";
    public static final String OPAQUE = "opaque";
    public static final String QOP = "qop";
    public static final String REALM = "realm";
    public static final String RESPONSE = "response";
    public static final String RESPONSE_AUTH = "rspauth";
    public static final String STALE = "stale";
    public static final String URI = "uri";
    public static final String USERNAME = "username";


    /**
     * Extracts a token from a header that has a given key. For instance if the header is
     * <p>
     * content-type=multipart/form-data boundary=myboundary
     * and the key is boundary the myboundary will be returned.
     *
     * @param header The header
     * @param key    The key that identifies the token to extract
     * @return The token, or null if it was not found
     */
    @Deprecated
    public static String extractTokenFromHeader(final String header, final String key) {
        int pos = header.indexOf(' ' + key + '=');
        if (pos == -1) {
            if(!header.startsWith(key + '=')) {
                return null;
            }
            pos = 0;
        } else {
            pos++;
        }
        int end;
        int start = pos + key.length() + 1;
        for (end = start; end < header.length(); ++end) {
            char c = header.charAt(end);
            if (c == ' ' || c == '\t' || c == ';') {
                break;
            }
        }
        return header.substring(start, end);
    }
    /**
     * Extracts a quoted value from a header that has a given key. For instance if the header is
     * <p>
     * content-disposition=form-data; name="my field"
     * and the key is name then "my field" will be returned without the quotes.
     *
     *
     * @param header The header
     * @param key    The key that identifies the token to extract
     * @return The token, or null if it was not found
     */
    public static String extractQuotedValueFromHeader(final String header, final String key) {

        int keypos = 0;
        int pos = -1;
        boolean whiteSpace = true;
        boolean inQuotes = false;
        for (int i = 0; i < header.length() - 1; ++i) { //-1 because we need room for the = at the end
            //TODO: a more efficient matching algorithm
            char c = header.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    inQuotes = false;
                }
            } else {
                if (key.charAt(keypos) == c && (whiteSpace || keypos > 0)) {
                    keypos++;
                    whiteSpace = false;
                } else if (c == '"') {
                    keypos = 0;
                    inQuotes = true;
                    whiteSpace = false;
                } else {
                    keypos = 0;
                    whiteSpace = c == ' ' || c == ';' || c == '\t';
                }
                if (keypos == key.length()) {
                    if (header.charAt(i + 1) == '=') {
                        pos = i + 2;
                        break;
                    } else {
                        keypos = 0;
                    }
                }
            }

        }
        if (pos == -1) {
            return null;
        }

        int end;
        int start = pos;
        if (header.charAt(start) == '"') {
            start++;
            for (end = start; end < header.length(); ++end) {
                char c = header.charAt(end);
                if (c == '"') {
                    break;
                }
            }
            return header.substring(start, end);

        } else {
            //no quotes
            for (end = start; end < header.length(); ++end) {
                char c = header.charAt(end);
                if (c == ' ' || c == '\t' || c == ';') {
                    break;
                }
            }
            return header.substring(start, end);
        }
    }

    /**
     * Extracts a quoted value from a header that has a given key. For instance if the header is
     * <p>
     * content-disposition=form-data; filename*="utf-8''test.txt"
     * and the key is filename* then "test.txt" will be returned after extracting character set and language
     * (following RFC 2231) and performing URL decoding to the value using the specified encoding
     *
     * @param header The header
     * @param key    The key that identifies the token to extract
     * @return The token, or null if it was not found
     */
    public static String extractQuotedValueFromHeaderWithEncoding(final String header, final String key) {
        String value = extractQuotedValueFromHeader(header, key);
        if (value != null) {
            return value;
        }
        value = extractQuotedValueFromHeader(header , key + "*");
        if(value != null) {
            int characterSetDelimiter = value.indexOf('\'');
            int languageDelimiter = value.lastIndexOf('\'', characterSetDelimiter + 1);
            String characterSet = value.substring(0, characterSetDelimiter);
            try {
                String fileNameURLEncoded = value.substring(languageDelimiter + 1);
                return URLDecoder.decode(fileNameURLEncoded, characterSet);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
