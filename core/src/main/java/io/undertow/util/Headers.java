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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * NOTE: if you add a new header here you must also add it to {@link io.undertow.server.protocol.http.HttpRequestParser}
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
    public static final String SEC_WEB_SOCKET_EXTENSIONS_STRING = "Sec-WebSocket-Extensions";
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
    public static final String SSL_CLIENT_CERT_STRING = "SSL_CLIENT_CERT";
    public static final String SSL_CIPHER_STRING = "SSL_CIPHER";
    public static final String SSL_SESSION_ID_STRING = "SSL_SESSION_ID";
    public static final String SSL_CIPHER_USEKEYSIZE_STRING = "SSL_CIPHER_USEKEYSIZE";
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
    public static final String X_FORWARDED_FOR_STRING = "X-Forwarded-For";
    public static final String X_FORWARDED_PROTO_STRING = "X-Forwarded-Proto";
    public static final String X_FORWARDED_HOST_STRING = "X-Forwarded-Host";
    public static final String X_FORWARDED_PORT_STRING = "X-Forwarded-Port";
    public static final String X_DISABLE_PUSH_STRING = "X-Disable-Push";
    public static final String X_FORWARDED_SERVER_STRING = "X-Forwarded-Server";

    // Header names

    public static final HttpString ACCEPT = new HttpString(ACCEPT_STRING, 1);
    public static final HttpString ACCEPT_CHARSET = new HttpString(ACCEPT_CHARSET_STRING, 2);
    public static final HttpString ACCEPT_ENCODING = new HttpString(ACCEPT_ENCODING_STRING, 3);
    public static final HttpString ACCEPT_LANGUAGE = new HttpString(ACCEPT_LANGUAGE_STRING, 4);
    public static final HttpString ACCEPT_RANGES = new HttpString(ACCEPT_RANGES_STRING, 5);
    public static final HttpString AGE = new HttpString(AGE_STRING, 6);
    public static final HttpString ALLOW = new HttpString(ALLOW_STRING, 7);
    public static final HttpString AUTHENTICATION_INFO = new HttpString(AUTHENTICATION_INFO_STRING, 8);
    public static final HttpString AUTHORIZATION = new HttpString(AUTHORIZATION_STRING, 9);
    public static final HttpString CACHE_CONTROL = new HttpString(CACHE_CONTROL_STRING, 10);
    public static final HttpString CONNECTION = new HttpString(CONNECTION_STRING, 11);
    public static final HttpString CONTENT_DISPOSITION = new HttpString(CONTENT_DISPOSITION_STRING, 12);
    public static final HttpString CONTENT_ENCODING = new HttpString(CONTENT_ENCODING_STRING, 13);
    public static final HttpString CONTENT_LANGUAGE = new HttpString(CONTENT_LANGUAGE_STRING, 14);
    public static final HttpString CONTENT_LENGTH = new HttpString(CONTENT_LENGTH_STRING, 15);
    public static final HttpString CONTENT_LOCATION = new HttpString(CONTENT_LOCATION_STRING, 16);
    public static final HttpString CONTENT_MD5 = new HttpString(CONTENT_MD5_STRING, 17);
    public static final HttpString CONTENT_RANGE = new HttpString(CONTENT_RANGE_STRING, 18);
    public static final HttpString CONTENT_TYPE = new HttpString(CONTENT_TYPE_STRING, 19);
    public static final HttpString COOKIE = new HttpString(COOKIE_STRING, 20);
    public static final HttpString COOKIE2 = new HttpString(COOKIE2_STRING, 21);
    public static final HttpString DATE = new HttpString(DATE_STRING, 22);
    public static final HttpString ETAG = new HttpString(ETAG_STRING, 23);
    public static final HttpString EXPECT = new HttpString(EXPECT_STRING, 24);
    public static final HttpString EXPIRES = new HttpString(EXPIRES_STRING, 25);
    public static final HttpString FROM = new HttpString(FROM_STRING, 26);
    public static final HttpString HOST = new HttpString(HOST_STRING, 27);
    public static final HttpString IF_MATCH = new HttpString(IF_MATCH_STRING, 28);
    public static final HttpString IF_MODIFIED_SINCE = new HttpString(IF_MODIFIED_SINCE_STRING, 29);
    public static final HttpString IF_NONE_MATCH = new HttpString(IF_NONE_MATCH_STRING, 30);
    public static final HttpString IF_RANGE = new HttpString(IF_RANGE_STRING, 31);
    public static final HttpString IF_UNMODIFIED_SINCE = new HttpString(IF_UNMODIFIED_SINCE_STRING, 32);
    public static final HttpString LAST_MODIFIED = new HttpString(LAST_MODIFIED_STRING, 33);
    public static final HttpString LOCATION = new HttpString(LOCATION_STRING, 34);
    public static final HttpString MAX_FORWARDS = new HttpString(MAX_FORWARDS_STRING, 35);
    public static final HttpString ORIGIN = new HttpString(ORIGIN_STRING, 36);
    public static final HttpString PRAGMA = new HttpString(PRAGMA_STRING, 37);
    public static final HttpString PROXY_AUTHENTICATE = new HttpString(PROXY_AUTHENTICATE_STRING, 38);
    public static final HttpString PROXY_AUTHORIZATION = new HttpString(PROXY_AUTHORIZATION_STRING, 39);
    public static final HttpString RANGE = new HttpString(RANGE_STRING, 40);
    public static final HttpString REFERER = new HttpString(REFERER_STRING, 41);
    public static final HttpString REFRESH = new HttpString(REFRESH_STRING, 42);
    public static final HttpString RETRY_AFTER = new HttpString(RETRY_AFTER_STRING, 43);
    public static final HttpString SEC_WEB_SOCKET_ACCEPT = new HttpString(SEC_WEB_SOCKET_ACCEPT_STRING, 44);
    public static final HttpString SEC_WEB_SOCKET_EXTENSIONS = new HttpString(SEC_WEB_SOCKET_EXTENSIONS_STRING);
    public static final HttpString SEC_WEB_SOCKET_KEY = new HttpString(SEC_WEB_SOCKET_KEY_STRING, 45);
    public static final HttpString SEC_WEB_SOCKET_KEY1 = new HttpString(SEC_WEB_SOCKET_KEY1_STRING, 46);
    public static final HttpString SEC_WEB_SOCKET_KEY2 = new HttpString(SEC_WEB_SOCKET_KEY2_STRING, 47);
    public static final HttpString SEC_WEB_SOCKET_LOCATION = new HttpString(SEC_WEB_SOCKET_LOCATION_STRING, 48);
    public static final HttpString SEC_WEB_SOCKET_ORIGIN = new HttpString(SEC_WEB_SOCKET_ORIGIN_STRING, 49);
    public static final HttpString SEC_WEB_SOCKET_PROTOCOL = new HttpString(SEC_WEB_SOCKET_PROTOCOL_STRING, 50);
    public static final HttpString SEC_WEB_SOCKET_VERSION = new HttpString(SEC_WEB_SOCKET_VERSION_STRING, 51);
    public static final HttpString SERVER = new HttpString(SERVER_STRING, 52);
    public static final HttpString SERVLET_ENGINE = new HttpString(SERVLET_ENGINE_STRING, 53);
    public static final HttpString SET_COOKIE = new HttpString(SET_COOKIE_STRING, 54);
    public static final HttpString SET_COOKIE2 = new HttpString(SET_COOKIE2_STRING, 55);
    public static final HttpString SSL_CLIENT_CERT = new HttpString(SSL_CLIENT_CERT_STRING);
    public static final HttpString SSL_CIPHER = new HttpString(SSL_CIPHER_STRING);
    public static final HttpString SSL_SESSION_ID = new HttpString(SSL_SESSION_ID_STRING);
    public static final HttpString SSL_CIPHER_USEKEYSIZE = new HttpString(SSL_CIPHER_USEKEYSIZE_STRING);
    public static final HttpString STATUS = new HttpString(STATUS_STRING, 56);
    public static final HttpString STRICT_TRANSPORT_SECURITY = new HttpString(STRICT_TRANSPORT_SECURITY_STRING, 57);
    public static final HttpString TE = new HttpString(TE_STRING, 58);
    public static final HttpString TRAILER = new HttpString(TRAILER_STRING, 59);
    public static final HttpString TRANSFER_ENCODING = new HttpString(TRANSFER_ENCODING_STRING, 60);
    public static final HttpString UPGRADE = new HttpString(UPGRADE_STRING, 61);
    public static final HttpString USER_AGENT = new HttpString(USER_AGENT_STRING, 62);
    public static final HttpString VARY = new HttpString(VARY_STRING, 63);
    public static final HttpString VIA = new HttpString(VIA_STRING, 64);
    public static final HttpString WARNING = new HttpString(WARNING_STRING, 65);
    public static final HttpString WWW_AUTHENTICATE = new HttpString(WWW_AUTHENTICATE_STRING, 66);
    public static final HttpString X_DISABLE_PUSH = new HttpString(X_DISABLE_PUSH_STRING, 67);
    public static final HttpString X_FORWARDED_FOR = new HttpString(X_FORWARDED_FOR_STRING, 68);
    public static final HttpString X_FORWARDED_HOST = new HttpString(X_FORWARDED_HOST_STRING, 69);
    public static final HttpString X_FORWARDED_PORT = new HttpString(X_FORWARDED_PORT_STRING, 70);
    public static final HttpString X_FORWARDED_PROTO = new HttpString(X_FORWARDED_PROTO_STRING, 71);
    public static final HttpString X_FORWARDED_SERVER = new HttpString(X_FORWARDED_SERVER_STRING, 72);

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


    private static final Map<String, HttpString> HTTP_STRING_MAP;

    static {
        Map<String, HttpString> map = AccessController.doPrivileged(new PrivilegedAction<Map<String, HttpString>>() {
            @Override
            public Map<String, HttpString> run() {
                Map<String, HttpString> map = new HashMap<>();
                Field[] fields = Headers.class.getDeclaredFields();
                for(Field field : fields) {
                    if(Modifier.isStatic(field.getModifiers()) && field.getType() == HttpString.class) {
                        field.setAccessible(true);
                        try {
                            HttpString result = (HttpString) field.get(null);
                            map.put(result.toString(), result);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                return map;
            }
        });
        HTTP_STRING_MAP = Collections.unmodifiableMap(map);
    }

    public static HttpString fromCache(String string) {
        return HTTP_STRING_MAP.get(string);
    }

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
    public static String extractTokenFromHeader(final String header, final String key) {
        int pos = header.indexOf(key + '=');
        if (pos == -1) {
            return null;
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
     * @param header The header
     * @param key    The key that identifies the token to extract
     * @return The token, or null if it was not found
     */
    public static String extractQuotedValueFromHeader(final String header, final String key) {

        int keypos = 0;
        int pos = -1;
        boolean inQuotes = false;
        for (int i = 0; i < header.length() - 1; ++i) { //-1 because we need room for the = at the end
            //TODO: a more efficient matching algorithm
            char c = header.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    inQuotes = false;
                }
            } else {
                if (key.charAt(keypos) == c) {
                    keypos++;
                } else if (c == '"') {
                    keypos = 0;
                    inQuotes = true;
                } else {
                    keypos = 0;
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
}
