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
 * NOTE: if you add a new header here you must also add it to {@link io.undertow.server.HttpParser}
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Headers {

    private Headers() {
    }

    // Headers as strings

    public static final String ACCEPT_STRING = "Accept";
    public static final String ACCEPT_CHARSET_STRING = "Accept-Charset";
    public static final String ACCEPT_ENCODING_STRING = "Accept-Encoding";
    public static final String ACCEPT_LANGUAGE_STRING = "Accept-Language";
    public static final String ACCEPT_RANGES_STRING = "Accept-Ranges";
    public static final String AGE_STRING = "Age";
    public static final String ALLOW_STRING = "Allow";
    public static final String AUTHENTICATION_INFO_STRING = "Authentication-Info";
    public static final String AUTHORIZATION_STRING = "Authorization";
    public static final String CACHE_CONTROL_STRING = "Cache-Control";
    public static final String COOKIE_STRING = "Cookie";
    public static final String COOKIE2_STRING = "Cookie2";
    public static final String CONNECTION_STRING = "Connection";
    public static final String CONTENT_DISPOSITION_STRING = "Content-Disposition";
    public static final String CONTENT_ENCODING_STRING = "Content-Encoding";
    public static final String CONTENT_LANGUAGE_STRING = "Content-Language";
    public static final String CONTENT_LENGTH_STRING = "Content-Length";
    public static final String CONTENT_LOCATION_STRING = "Content-Location";
    public static final String CONTENT_MD5_STRING = "Content-MD5";
    public static final String CONTENT_RANGE_STRING = "Content-Range";
    public static final String CONTENT_TYPE_STRING = "Content-Type";
    public static final String DATE_STRING = "Date";
    public static final String ETAG_STRING = "ETag";
    public static final String EXPECT_STRING = "Expect";
    public static final String EXPIRES_STRING = "Expires";
    public static final String FROM_STRING = "From";
    public static final String HOST_STRING = "Host";
    public static final String IF_MATCH_STRING = "If-Match";
    public static final String IF_MODIFIED_SINCE_STRING = "If-Modified-Since";
    public static final String IF_NONE_MATCH_STRING = "If-None-Match";
    public static final String IF_RANGE_STRING = "If-Range";
    public static final String IF_UNMODIFIED_SINCE_STRING = "If-Unmodified-Since";
    public static final String LAST_MODIFIED_STRING = "Last-Modified";
    public static final String LOCATION_STRING = "Location";
    public static final String MAX_FORWARDS_STRING = "Max-Forwards";
    public static final String ORIGIN_STRING = "Origin";
    public static final String PRAGMA_STRING = "Pragma";
    public static final String PROXY_AUTHENTICATE_STRING = "Proxy-Authenticate";
    public static final String PROXY_AUTHORIZATION_STRING = "Proxy-Authorization";
    public static final String RANGE_STRING = "Range";
    public static final String REFERER_STRING = "Referer";
    public static final String REFRESH_STRING = "Refresh";
    public static final String RETRY_AFTER_STRING = "Retry-After";
    public static final String SEC_WEB_SOCKET_ACCEPT_STRING = "Sec-WebSocket-Accept";
    public static final String SEC_WEB_SOCKET_KEY_STRING = "Sec-WebSocket-Key";
    public static final String SEC_WEB_SOCKET_KEY1_STRING = "Sec-WebSocket-Key1";
    public static final String SEC_WEB_SOCKET_KEY2_STRING = "Sec-WebSocket-Key2";
    public static final String SEC_WEB_SOCKET_LOCATION_STRING = "Sec-WebSocket-Location";
    public static final String SEC_WEB_SOCKET_ORIGIN_STRING = "Sec-WebSocket-Origin";
    public static final String SEC_WEB_SOCKET_PROTOCOL_STRING = "Sec-WebSocket-Protocol";
    public static final String SEC_WEB_SOCKET_VERSION_STRING = "Sec-WebSocket-Version";
    public static final String SERVER_STRING = "Server";
    public static final String SERVLET_ENGINE_STRING = "Servlet-Engine";
    public static final String SET_COOKIE_STRING = "Set-Cookie";
    public static final String SET_COOKIE2_STRING = "Set-Cookie2";
    public static final String STATUS_STRING = "Status";
    public static final String STRICT_TRANSPORT_SECURITY_STRING = "Strict-Transport-Security";
    public static final String TE_STRING = "TE";
    public static final String TRAILER_STRING = "Trailer";
    public static final String TRANSFER_ENCODING_STRING = "Transfer-Encoding";
    public static final String UPGRADE_STRING = "Upgrade";
    public static final String USER_AGENT_STRING = "User-Agent";
    public static final String VARY_STRING = "Vary";
    public static final String VIA_STRING = "Via";
    public static final String WARNING_STRING = "Warning";
    public static final String WWW_AUTHENTICATE_STRING = "WWW-Authenticate";

    // Header names

    public static final HttpString ACCEPT = new HttpString(ACCEPT_STRING);
    public static final HttpString ACCEPT_CHARSET = new HttpString(ACCEPT_CHARSET_STRING);
    public static final HttpString ACCEPT_ENCODING = new HttpString(ACCEPT_ENCODING_STRING);
    public static final HttpString ACCEPT_LANGUAGE = new HttpString(ACCEPT_LANGUAGE_STRING);
    public static final HttpString ACCEPT_RANGES = new HttpString(ACCEPT_RANGES_STRING);
    public static final HttpString AGE = new HttpString(AGE_STRING);
    public static final HttpString ALLOW = new HttpString(ALLOW_STRING);
    public static final HttpString AUTHENTICATION_INFO = new HttpString(AUTHENTICATION_INFO_STRING);
    public static final HttpString AUTHORIZATION = new HttpString(AUTHORIZATION_STRING);
    public static final HttpString CACHE_CONTROL = new HttpString(CACHE_CONTROL_STRING);
    public static final HttpString COOKIE = new HttpString(COOKIE_STRING);
    public static final HttpString COOKIE2 = new HttpString(COOKIE2_STRING);
    public static final HttpString CONNECTION = new HttpString(CONNECTION_STRING);
    public static final HttpString CONTENT_DISPOSITION = new HttpString(CONTENT_DISPOSITION_STRING);
    public static final HttpString CONTENT_ENCODING = new HttpString(CONTENT_ENCODING_STRING);
    public static final HttpString CONTENT_LANGUAGE = new HttpString(CONTENT_LANGUAGE_STRING);
    public static final HttpString CONTENT_LENGTH = new HttpString(CONTENT_LENGTH_STRING);
    public static final HttpString CONTENT_LOCATION = new HttpString(CONTENT_LOCATION_STRING);
    public static final HttpString CONTENT_MD5 = new HttpString(CONTENT_MD5_STRING);
    public static final HttpString CONTENT_RANGE = new HttpString(CONTENT_RANGE_STRING);
    public static final HttpString CONTENT_TYPE = new HttpString(CONTENT_TYPE_STRING);
    public static final HttpString DATE = new HttpString(DATE_STRING);
    public static final HttpString ETAG = new HttpString(ETAG_STRING);
    public static final HttpString EXPECT = new HttpString(EXPECT_STRING);
    public static final HttpString EXPIRES = new HttpString(EXPIRES_STRING);
    public static final HttpString FROM = new HttpString(FROM_STRING);
    public static final HttpString HOST = new HttpString(HOST_STRING);
    public static final HttpString IF_MATCH = new HttpString(IF_MATCH_STRING);
    public static final HttpString IF_MODIFIED_SINCE = new HttpString(IF_MODIFIED_SINCE_STRING);
    public static final HttpString IF_NONE_MATCH = new HttpString(IF_NONE_MATCH_STRING);
    public static final HttpString IF_RANGE = new HttpString(IF_RANGE_STRING);
    public static final HttpString IF_UNMODIFIED_SINCE = new HttpString(IF_UNMODIFIED_SINCE_STRING);
    public static final HttpString LAST_MODIFIED = new HttpString(LAST_MODIFIED_STRING);
    public static final HttpString LOCATION = new HttpString(LOCATION_STRING);
    public static final HttpString MAX_FORWARDS = new HttpString(MAX_FORWARDS_STRING);
    public static final HttpString ORIGIN = new HttpString(ORIGIN_STRING);
    public static final HttpString PRAGMA = new HttpString(PRAGMA_STRING);
    public static final HttpString PROXY_AUTHENTICATE = new HttpString(PROXY_AUTHENTICATE_STRING);
    public static final HttpString PROXY_AUTHORIZATION = new HttpString(PROXY_AUTHORIZATION_STRING);
    public static final HttpString RANGE = new HttpString(RANGE_STRING);
    public static final HttpString REFERER = new HttpString(REFERER_STRING);
    public static final HttpString REFRESH = new HttpString(REFRESH_STRING);
    public static final HttpString RETRY_AFTER = new HttpString(RETRY_AFTER_STRING);
    public static final HttpString SEC_WEB_SOCKET_ACCEPT = new HttpString(SEC_WEB_SOCKET_ACCEPT_STRING);
    public static final HttpString SEC_WEB_SOCKET_KEY = new HttpString(SEC_WEB_SOCKET_KEY_STRING);
    public static final HttpString SEC_WEB_SOCKET_KEY1 = new HttpString(SEC_WEB_SOCKET_KEY1_STRING);
    public static final HttpString SEC_WEB_SOCKET_KEY2 = new HttpString(SEC_WEB_SOCKET_KEY2_STRING);
    public static final HttpString SEC_WEB_SOCKET_LOCATION = new HttpString(SEC_WEB_SOCKET_LOCATION_STRING);
    public static final HttpString SEC_WEB_SOCKET_ORIGIN = new HttpString(SEC_WEB_SOCKET_ORIGIN_STRING);
    public static final HttpString SEC_WEB_SOCKET_PROTOCOL = new HttpString(SEC_WEB_SOCKET_PROTOCOL_STRING);
    public static final HttpString SEC_WEB_SOCKET_VERSION = new HttpString(SEC_WEB_SOCKET_VERSION_STRING);
    public static final HttpString SERVER = new HttpString(SERVER_STRING);
    public static final HttpString SERVLET_ENGINE = new HttpString(SERVLET_ENGINE_STRING);
    public static final HttpString SET_COOKIE = new HttpString(SET_COOKIE_STRING);
    public static final HttpString SET_COOKIE2 = new HttpString(SET_COOKIE2_STRING);
    public static final HttpString STATUS = new HttpString(STATUS_STRING);
    public static final HttpString STRICT_TRANSPORT_SECURITY = new HttpString(STRICT_TRANSPORT_SECURITY_STRING);
    public static final HttpString TE = new HttpString(TE_STRING);
    public static final HttpString TRAILER = new HttpString(TRAILER_STRING);
    public static final HttpString TRANSFER_ENCODING = new HttpString(TRANSFER_ENCODING_STRING);
    public static final HttpString UPGRADE = new HttpString(UPGRADE_STRING);
    public static final HttpString USER_AGENT = new HttpString(USER_AGENT_STRING);
    public static final HttpString VARY = new HttpString(VARY_STRING);
    public static final HttpString VIA = new HttpString(VIA_STRING);
    public static final HttpString WARNING = new HttpString(WARNING_STRING);
    public static final HttpString WWW_AUTHENTICATE = new HttpString(WWW_AUTHENTICATE_STRING);

    // Content codings

    public static final HttpString COMPRESS = new HttpString("compress");
    public static final HttpString X_COMPRESS = new HttpString("x-compress");
    public static final HttpString DEFLATE = new HttpString("deflate");
    public static final HttpString IDENTITY = new HttpString("identity");
    public static final HttpString GZIP = new HttpString("gzip");
    public static final HttpString X_GZIP = new HttpString("x-gzip");

    // Transfer codings

    public static final HttpString CHUNKED = new HttpString("chunked");
    // IDENTITY
    // GZIP
    // COMPRESS
    // DEFLATE

    // Connection values
    public static final HttpString KEEP_ALIVE = new HttpString("keep-alive");
    public static final HttpString CLOSE = new HttpString("close");

    //MIME header used in multipart file uploads
    public static final String CONTENT_TRANSFER_ENCODING_STRING = "Content-Transfer-Encoding";
    public static final HttpString CONTENT_TRANSFER_ENCODING = new HttpString(CONTENT_TRANSFER_ENCODING_STRING);

    // Authentication Schemes
    public static final HttpString BASIC = new HttpString("Basic");
    public static final HttpString DIGEST = new HttpString("Digest");
    public static final HttpString NEGOTIATE = new HttpString("Negotiate");

    // Digest authentication Token Names
    public static final HttpString ALGORITHM = new HttpString("algorithm");
    public static final HttpString AUTH_PARAM = new HttpString("auth-param");
    public static final HttpString CNONCE = new HttpString("cnonce");
    public static final HttpString DOMAIN = new HttpString("domain");
    public static final HttpString NEXT_NONCE = new HttpString("nextnonce");
    public static final HttpString NONCE = new HttpString("nonce");
    public static final HttpString NONCE_COUNT = new HttpString("nc");
    public static final HttpString OPAQUE = new HttpString("opaque");
    public static final HttpString QOP = new HttpString("qop");
    public static final HttpString REALM = new HttpString("realm");
    public static final HttpString RESPONSE = new HttpString("response");
    public static final HttpString RESPONSE_AUTH = new HttpString("rspauth");
    public static final HttpString STALE = new HttpString("stale");
    public static final HttpString URI = new HttpString("uri");
    public static final HttpString USERNAME = new HttpString("username");



    /**
     * Extracts a token from a header that has a given key. For instance if the header is
     * <p/>
     * content-type=multipart/form-data boundary=myboundary
     * and the key is boundary the myboundary will be returned.
     *
     * @param header The header
     * @param key    The key that identifies the token to extract
     * @return The token, or null if it was not found
     */
    public static String extractTokenFromHeader(final String header, final String key) {
        int pos = header.indexOf(key + '=');
        if (pos == -1) {
            return null;
        }
        int end;
        int start = pos + key.length() + 1;
        for (end = start; end < header.length(); ++end) {
            char c = header.charAt(end);
            if (c == ' ' || c == '\t') {
                break;
            }
        }
        return header.substring(start, end);
    }

    /**
     * Extracts a quoted value from a header that has a given key. For instance if the header is
     * <p/>
     * content-disposition=form-data; name="my field"
     * and the key is name then "my field" will be returned without the quotes.
     *
     * @param header The header
     * @param key    The key that identifies the token to extract
     * @return The token, or null if it was not found
     */
    public static String extractQuotedValueFromHeader(final String header, final String key) {
        int pos = header.indexOf(key + '=');
        if (pos == -1) {
            return null;
        }

        int end;
        int start = pos + key.length() + 1;
        boolean quotes = false;
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
                if (c == ' ' || c == '\t') {
                    break;
                }
            }
            return header.substring(start, end);
        }
    }
}
