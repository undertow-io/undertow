package io.undertow.protocols.ajp;

import static io.undertow.util.Methods.ACL;
import static io.undertow.util.Methods.BASELINE_CONTROL;
import static io.undertow.util.Methods.CHECKIN;
import static io.undertow.util.Methods.CHECKOUT;
import static io.undertow.util.Methods.COPY;
import static io.undertow.util.Methods.DELETE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.HEAD;
import static io.undertow.util.Methods.LABEL;
import static io.undertow.util.Methods.LOCK;
import static io.undertow.util.Methods.MERGE;
import static io.undertow.util.Methods.MKACTIVITY;
import static io.undertow.util.Methods.MKCOL;
import static io.undertow.util.Methods.MKWORKSPACE;
import static io.undertow.util.Methods.MOVE;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.Methods.PROPFIND;
import static io.undertow.util.Methods.PROPPATCH;
import static io.undertow.util.Methods.PUT;
import static io.undertow.util.Methods.REPORT;
import static io.undertow.util.Methods.SEARCH;
import static io.undertow.util.Methods.TRACE;
import static io.undertow.util.Methods.UNCHECKOUT;
import static io.undertow.util.Methods.UNLOCK;
import static io.undertow.util.Methods.UPDATE;
import static io.undertow.util.Methods.VERSION_CONTROL;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.undertow.util.Headers;
import io.undertow.util.HttpString;

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
        methods.put(OPTIONS, 1);
        methods.put(GET, 2);
        methods.put(HEAD, 3);
        methods.put(POST, 4);
        methods.put(PUT, 5);
        methods.put(DELETE, 6);
        methods.put(TRACE, 7);
        methods.put(PROPFIND, 8);
        methods.put(PROPPATCH, 9);
        methods.put(MKCOL, 10);
        methods.put(COPY, 11);
        methods.put(MOVE, 12);
        methods.put(LOCK, 13);
        methods.put(UNLOCK, 14);
        methods.put(ACL, 15);
        methods.put(REPORT, 16);
        methods.put(VERSION_CONTROL, 17);
        methods.put(CHECKIN, 18);
        methods.put(CHECKOUT, 19);
        methods.put(UNCHECKOUT, 20);
        methods.put(SEARCH, 21);
        methods.put(MKWORKSPACE, 22);
        methods.put(UPDATE, 23);
        methods.put(LABEL, 24);
        methods.put(MERGE, 25);
        methods.put(BASELINE_CONTROL, 26);
        methods.put(MKACTIVITY, 27);
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
