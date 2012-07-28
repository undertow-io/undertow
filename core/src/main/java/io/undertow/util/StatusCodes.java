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
public enum StatusCodes {
    UNKNOWN(0, "Unknown"),
    CODE_100(100, "Continue"),
    CODE_101(101, "Switching Protocols"),
    CODE_200(200, "OK"),
    CODE_201(201, "Created"),
    CODE_202(202, "Accepted"),
    CODE_203(203, "Non-Authoritative Information"),
    CODE_204(204, "No Content"),
    CODE_205(205, "Reset Content"),
    CODE_206(206, "Partial Content"),
    CODE_300(300, "Multiple Choices"),
    CODE_301(301, "Moved Permanently"),
    CODE_302(302, "Found"),
    CODE_303(303, "See Other"),
    CODE_304(304, "Not Modified"),
    CODE_305(305, "Use Proxy"),
    CODE_307(307, "Temporary Redirect"),
    CODE_400(400, "Bad Request"),
    CODE_401(401, "Unauthorized"),
    CODE_402(402, "Payment Required"),
    CODE_403(403, "Forbidden"),
    CODE_404(404, "Not Found"),
    CODE_405(405, "Method Not Allowed"),
    CODE_406(406, "Not Acceptable"),
    CODE_407(407, "Proxy Authentication Required"),
    CODE_408(408, "Request Time-out"),
    CODE_409(409, "Conflict"),
    CODE_410(410, "Gone"),
    CODE_411(411, "Length Required"),
    CODE_412(412, "Precondition Failed"),
    CODE_413(413, "Request Entity Too Large"),
    CODE_414(414, "Request-URI Too Large"),
    CODE_415(415, "Unsupported Media Type"),
    CODE_416(416, "Requested range not satisfiable"),
    CODE_417(417, "Expectation Failed"),
    CODE_500(500, "Internal Server Error"),
    CODE_501(501, "Not Implemented"),
    CODE_502(502, "Bad Gateway"),
    CODE_503(503, "Service Unavailable"),
    CODE_504(504, "Gateway Time-out"),
    CODE_505(505, "HTTP Version not supported"),;

    private final int code;
    private final String reason;

    private static final Map<Integer, StatusCodes> CODES;

    static {
        final Map<Integer, StatusCodes> codes = new HashMap<Integer, StatusCodes>();
        for (final StatusCodes code : values()) {
            codes.put(code.code, code);
        }
        CODES = Collections.unmodifiableMap(codes);
    }

    private StatusCodes(final int code, final String reason) {
        this.code = code;
        this.reason = reason;
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    public static final String getReason(final int code) {
        final StatusCodes result = CODES.get(code);
        if (result == null) {
            return UNKNOWN.reason;
        } else {
            return result.reason;
        }
    }
}
