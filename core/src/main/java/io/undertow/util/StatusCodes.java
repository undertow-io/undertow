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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class StatusCodes {
    public static final int CONTINUE = 100;
    public static final int SWITCHING_PROTOCOLS = 101;
    public static final int OK = 200;
    public static final int CREATED = 201;
    public static final int ACCEPTED = 202;
    public static final int NON_AUTHORITATIVE_INFORMATION = 203;
    public static final int NO_CONTENT = 204;
    public static final int RESET_CONTENT = 205;
    public static final int PARTIAL_CONTENT = 206;
    public static final int MULTIPLE_CHOICES = 300;
    public static final int MOVED_PERMENANTLY = 301;
    public static final int FOUND = 302;
    public static final int SEE_OTHER = 303;
    public static final int NOT_MODIFIED = 304;
    public static final int USE_PROXY = 305;
    public static final int TEMPORARY_REDIRECT = 307;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int PAYMENT_REQUIRED = 402;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int NOT_ACCEPTABLE = 406;
    public static final int PROXY_AUTHENTICATION_REQUIRED = 407;
    public static final int REQUEST_TIME_OUT = 408;
    public static final int CONFLICT = 409;
    public static final int GONE = 410;
    public static final int LENGTH_REQUIRED = 411;
    public static final int PRECONDITION_FAILED = 412;
    public static final int REQUEST_ENTITY_TOO_LARGE = 413;
    public static final int REQUEST_URI_TOO_LARGE = 414;
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;
    public static final int REQUEST_RANGE_NOT_SATISFIABLE = 416;
    public static final int EXPECTATION_FAILED = 417;
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int NOT_IMPLEMENTED = 501;
    public static final int BAD_GATEWAY = 502;
    public static final int SERVICE_UNAVAILABLE = 503;
    public static final int GATEWAY_TIME_OUT = 504;
    public static final int HTTP_VERSION_NOT_SUPPORTED = 505;


    public static final String CONTINUE_STRING = "Continue";
    public static final String SWITCHING_PROTOCOLS_STRING = "Switching Protocols";
    public static final String OK_STRING = "OK";
    public static final String CREATED_STRING = "Created";
    public static final String ACCEPTED_STRING = "Accepted";
    public static final String NON_AUTHORITATIVE_INFORMATION_STRING = "Non-Authoritative Information";
    public static final String NO_CONTENT_STRING = "No Content";
    public static final String RESET_CONTENT_STRING = "Reset Content";
    public static final String PARTIAL_CONTENT_STRING = "Partial Content";
    public static final String MULTIPLE_CHOICES_STRING = "Multiple Choices";
    public static final String MOVED_PERMENANTLY_STRING = "Moved Permanently";
    public static final String FOUND_STRING = "Found";
    public static final String SEE_OTHER_STRING = "See Other";
    public static final String NOT_MODIFIED_STRING = "Not Modified";
    public static final String USE_PROXY_STRING = "Use Proxy";
    public static final String TEMPORARY_REDIRECT_STRING = "Temporary Redirect";
    public static final String BAD_REQUEST_STRING = "Bad Request";
    public static final String UNAUTHORIZED_STRING = "Unauthorized";
    public static final String PAYMENT_REQUIRED_STRING = "Payment Required";
    public static final String FORBIDDEN_STRING = "Forbidden";
    public static final String NOT_FOUND_STRING = "Not Found";
    public static final String METHOD_NOT_ALLOWED_STRING = "Method Not Allowed";
    public static final String NOT_ACCEPTABLE_STRING = "Not Acceptable";
    public static final String PROXY_AUTHENTICATION_REQUIRED_STRING = "Proxy Authentication Required";
    public static final String REQUEST_TIME_OUT_STRING = "Request Time-out";
    public static final String CONFLICT_STRING = "Conflict";
    public static final String GONE_STRING = "Gone";
    public static final String LENGTH_REQUIRED_STRING = "Length Required";
    public static final String PRECONDITION_FAILED_STRING = "Precondition Failed";
    public static final String REQUEST_ENTITY_TOO_LARGE_STRING = "Request Entity Too Large";
    public static final String REQUEST_URI_TOO_LARGE_STRING = "Request-URI Too Large";
    public static final String UNSUPPORTED_MEDIA_TYPE_STRING = "Unsupported Media Type";
    public static final String REQUEST_RANGE_NOT_SATISFIABLE_STRING = "Requested range not satisfiable";
    public static final String EXPECTATION_FAILED_STRING = "Expectation Failed";
    public static final String INTERNAL_SERVER_ERROR_STRING = "Internal Server Error";
    public static final String NOT_IMPLEMENTED_STRING = "Not Implemented";
    public static final String BAD_GATEWAY_STRING = "Bad Gateway";
    public static final String SERVICE_UNAVAILABLE_STRING = "Service Unavailable";
    public static final String GATEWAY_TIME_OUT_STRING = "Gateway Time-out";
    public static final String HTTP_VERSION_NOT_SUPPORTED_STRING = "HTTP Version not supported";
    ;

    private static final Map<Integer, String> CODES;

    static {
        final Map<Integer, String> codes = new HashMap<Integer, String>();
        codes.put(CONTINUE, CONTINUE_STRING);
        codes.put(SWITCHING_PROTOCOLS, SWITCHING_PROTOCOLS_STRING);
        codes.put(OK, OK_STRING);
        codes.put(CREATED, CREATED_STRING);
        codes.put(ACCEPTED, ACCEPTED_STRING);
        codes.put(NON_AUTHORITATIVE_INFORMATION, NON_AUTHORITATIVE_INFORMATION_STRING);
        codes.put(NO_CONTENT, NO_CONTENT_STRING);
        codes.put(RESET_CONTENT, RESET_CONTENT_STRING);
        codes.put(PARTIAL_CONTENT, PARTIAL_CONTENT_STRING);
        codes.put(MULTIPLE_CHOICES, MULTIPLE_CHOICES_STRING);
        codes.put(MOVED_PERMENANTLY, MOVED_PERMENANTLY_STRING);
        codes.put(FOUND, FOUND_STRING);
        codes.put(SEE_OTHER, SEE_OTHER_STRING);
        codes.put(NOT_MODIFIED, NOT_MODIFIED_STRING);
        codes.put(USE_PROXY, USE_PROXY_STRING);
        codes.put(TEMPORARY_REDIRECT, TEMPORARY_REDIRECT_STRING);
        codes.put(BAD_REQUEST, BAD_REQUEST_STRING);
        codes.put(UNAUTHORIZED, UNAUTHORIZED_STRING);
        codes.put(PAYMENT_REQUIRED, PAYMENT_REQUIRED_STRING);
        codes.put(FORBIDDEN, FORBIDDEN_STRING);
        codes.put(NOT_FOUND, NOT_FOUND_STRING);
        codes.put(METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_STRING);
        codes.put(NOT_ACCEPTABLE, NOT_ACCEPTABLE_STRING);
        codes.put(PROXY_AUTHENTICATION_REQUIRED, PROXY_AUTHENTICATION_REQUIRED_STRING);
        codes.put(REQUEST_TIME_OUT, REQUEST_TIME_OUT_STRING);
        codes.put(CONFLICT, CONFLICT_STRING);
        codes.put(GONE, GONE_STRING);
        codes.put(LENGTH_REQUIRED, LENGTH_REQUIRED_STRING);
        codes.put(PRECONDITION_FAILED, PRECONDITION_FAILED_STRING);
        codes.put(REQUEST_ENTITY_TOO_LARGE, REQUEST_ENTITY_TOO_LARGE_STRING);
        codes.put(REQUEST_URI_TOO_LARGE, REQUEST_URI_TOO_LARGE_STRING);
        codes.put(UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA_TYPE_STRING);
        codes.put(REQUEST_RANGE_NOT_SATISFIABLE, REQUEST_RANGE_NOT_SATISFIABLE_STRING);
        codes.put(EXPECTATION_FAILED, EXPECTATION_FAILED_STRING);
        codes.put(INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_STRING);
        codes.put(NOT_IMPLEMENTED, NOT_IMPLEMENTED_STRING);
        codes.put(BAD_GATEWAY, BAD_GATEWAY_STRING);
        codes.put(SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE_STRING);
        codes.put(GATEWAY_TIME_OUT, GATEWAY_TIME_OUT_STRING);
        codes.put(HTTP_VERSION_NOT_SUPPORTED, HTTP_VERSION_NOT_SUPPORTED_STRING);


        CODES = Collections.unmodifiableMap(codes);
    }

    private StatusCodes() {
    }

    public static final String getReason(final int code) {
        final String result = CODES.get(code);
        if (result == null) {
            return "Unknown";
        } else {
            return result;
        }
    }
}
