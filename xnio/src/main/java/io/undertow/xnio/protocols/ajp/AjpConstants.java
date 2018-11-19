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

package io.undertow.xnio.protocols.ajp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

/**
 * @author Stuart Douglas
 */
class AjpConstants {


    public static final int FRAME_TYPE_SEND_HEADERS = 4;
    public static final int FRAME_TYPE_REQUEST_BODY_CHUNK = 6;
    public static final int FRAME_TYPE_SEND_BODY_CHUNK = 3;
    public static final int FRAME_TYPE_END_RESPONSE = 5;
    public static final int FRAME_TYPE_CPONG = 9;
    public static final int FRAME_TYPE_CPING = 10;
    public static final int FRAME_TYPE_SHUTDOWN = 7;


    static final Map<HttpString, Integer> HEADER_MAP;
    static final Map<HttpString, Integer> HTTP_METHODS_MAP;
    static final HttpString[] HTTP_HEADERS_ARRAY;

    static final int ATTR_CONTEXT = 0x01;
    static final int ATTR_SERVLET_PATH = 0x02;
    static final int ATTR_REMOTE_USER = 0x03;
    static final int ATTR_AUTH_TYPE = 0x04;
    static final int ATTR_QUERY_STRING = 0x05;
    static final int ATTR_ROUTE = 0x06;
    static final int ATTR_SSL_CERT = 0x07;
    static final int ATTR_SSL_CIPHER = 0x08;
    static final int ATTR_SSL_SESSION = 0x09;
    static final int ATTR_REQ_ATTRIBUTE = 0x0A;
    static final int ATTR_SSL_KEY_SIZE = 0x0B;
    static final int ATTR_SECRET = 0x0C;
    static final int ATTR_STORED_METHOD = 0x0D;
    static final int ATTR_ARE_DONE = 0xFF;


    static {
        final Map<HttpString, Integer> headers = new HashMap<>();
        headers.put(Headers.ACCEPT, 0xA001);
        headers.put(Headers.ACCEPT_CHARSET, 0xA002);
        headers.put(Headers.ACCEPT_ENCODING, 0xA003);
        headers.put(Headers.ACCEPT_LANGUAGE, 0xA004);
        headers.put(Headers.AUTHORIZATION, 0xA005);
        headers.put(Headers.CONNECTION, 0xA006);
        headers.put(Headers.CONTENT_TYPE, 0xA007);
        headers.put(Headers.CONTENT_LENGTH, 0xA008);
        headers.put(Headers.COOKIE, 0xA009);
        headers.put(Headers.COOKIE2, 0xA00A);
        headers.put(Headers.HOST, 0xA00B);
        headers.put(Headers.PRAGMA, 0xA00C);
        headers.put(Headers.REFERER, 0xA00D);
        headers.put(Headers.USER_AGENT, 0xA00E);

        HEADER_MAP = Collections.unmodifiableMap(headers);

        final Map<HttpString, Integer> methods = new HashMap<>();
        methods.put(Methods.OPTIONS, 1);
        methods.put(Methods.GET, 2);
        methods.put(Methods.HEAD, 3);
        methods.put(Methods.POST, 4);
        methods.put(Methods.PUT, 5);
        methods.put(Methods.DELETE, 6);
        methods.put(Methods.TRACE, 7);
        methods.put(Methods.PROPFIND, 8);
        methods.put(Methods.PROPPATCH, 9);
        methods.put(Methods.MKCOL, 10);
        methods.put(Methods.COPY, 11);
        methods.put(Methods.MOVE, 12);
        methods.put(Methods.LOCK, 13);
        methods.put(Methods.UNLOCK, 14);
        methods.put(Methods.ACL, 15);
        methods.put(Methods.REPORT, 16);
        methods.put(Methods.VERSION_CONTROL, 17);
        methods.put(Methods.CHECKIN, 18);
        methods.put(Methods.CHECKOUT, 19);
        methods.put(Methods.UNCHECKOUT, 20);
        methods.put(Methods.SEARCH, 21);
        methods.put(Methods.MKWORKSPACE, 22);
        methods.put(Methods.UPDATE, 23);
        methods.put(Methods.LABEL, 24);
        methods.put(Methods.MERGE, 25);
        methods.put(Methods.BASELINE_CONTROL, 26);
        methods.put(Methods.MKACTIVITY, 27);
        HTTP_METHODS_MAP = Collections.unmodifiableMap(methods);

        HTTP_HEADERS_ARRAY = new HttpString[]{null,
                Headers.CONTENT_TYPE,
                Headers.CONTENT_LANGUAGE,
                Headers.CONTENT_LENGTH,
                Headers.DATE,
                Headers.LAST_MODIFIED,
                Headers.LOCATION,
                Headers.SET_COOKIE,
                Headers.SET_COOKIE2,
                Headers.SERVLET_ENGINE,
                Headers.STATUS,
                Headers.WWW_AUTHENTICATE
        };
    }
}
