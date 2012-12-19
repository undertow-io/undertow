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

package io.undertow.server;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import io.undertow.annotationprocessor.HttpParserConfig;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;

import static io.undertow.util.Headers.ACCEPT_CHARSET_STRING;
import static io.undertow.util.Headers.ACCEPT_ENCODING_STRING;
import static io.undertow.util.Headers.ACCEPT_LANGUAGE_STRING;
import static io.undertow.util.Headers.ACCEPT_RANGES_STRING;
import static io.undertow.util.Headers.ACCEPT_STRING;
import static io.undertow.util.Headers.AGE_STRING;
import static io.undertow.util.Headers.ALLOW_STRING;
import static io.undertow.util.Headers.AUTHORIZATION_STRING;
import static io.undertow.util.Headers.CACHE_CONTROL_STRING;
import static io.undertow.util.Headers.CONNECTION_STRING;
import static io.undertow.util.Headers.CONTENT_DISPOSITION_STRING;
import static io.undertow.util.Headers.CONTENT_ENCODING_STRING;
import static io.undertow.util.Headers.CONTENT_LANGUAGE_STRING;
import static io.undertow.util.Headers.CONTENT_LENGTH_STRING;
import static io.undertow.util.Headers.CONTENT_LOCATION_STRING;
import static io.undertow.util.Headers.CONTENT_MD5_STRING;
import static io.undertow.util.Headers.CONTENT_RANGE_STRING;
import static io.undertow.util.Headers.CONTENT_TYPE_STRING;
import static io.undertow.util.Headers.COOKIE_STRING;
import static io.undertow.util.Headers.DATE_STRING;
import static io.undertow.util.Headers.ETAG_STRING;
import static io.undertow.util.Headers.EXPECT_STRING;
import static io.undertow.util.Headers.EXPIRES_STRING;
import static io.undertow.util.Headers.FROM_STRING;
import static io.undertow.util.Headers.HOST_STRING;
import static io.undertow.util.Headers.IF_MATCH_STRING;
import static io.undertow.util.Headers.IF_MODIFIED_SINCE_STRING;
import static io.undertow.util.Headers.IF_NONE_MATCH_STRING;
import static io.undertow.util.Headers.IF_RANGE_STRING;
import static io.undertow.util.Headers.IF_UNMODIFIED_SINCE_STRING;
import static io.undertow.util.Headers.LAST_MODIFIED_STRING;
import static io.undertow.util.Headers.LOCATION_STRING;
import static io.undertow.util.Headers.MAX_FORWARDS_STRING;
import static io.undertow.util.Headers.ORIGIN_STRING;
import static io.undertow.util.Headers.PRAGMA_STRING;
import static io.undertow.util.Headers.PROXY_AUTHENTICATE_STRING;
import static io.undertow.util.Headers.PROXY_AUTHORIZATION_STRING;
import static io.undertow.util.Headers.RANGE_STRING;
import static io.undertow.util.Headers.REFERER_STRING;
import static io.undertow.util.Headers.REFRESH_STRING;
import static io.undertow.util.Headers.RETRY_AFTER_STRING;
import static io.undertow.util.Headers.SEC_WEB_SOCKET_KEY_STRING;
import static io.undertow.util.Headers.SEC_WEB_SOCKET_VERSION_STRING;
import static io.undertow.util.Headers.SERVER_STRING;
import static io.undertow.util.Headers.SET_COOKIE2_STRING;
import static io.undertow.util.Headers.SET_COOKIE_STRING;
import static io.undertow.util.Headers.STRICT_TRANSPORT_SECURITY_STRING;
import static io.undertow.util.Headers.TE_STRING;
import static io.undertow.util.Headers.TRAILER_STRING;
import static io.undertow.util.Headers.TRANSFER_ENCODING_STRING;
import static io.undertow.util.Headers.UPGRADE_STRING;
import static io.undertow.util.Headers.USER_AGENT_STRING;
import static io.undertow.util.Headers.VARY_STRING;
import static io.undertow.util.Headers.VIA_STRING;
import static io.undertow.util.Headers.WARNING_STRING;
import static io.undertow.util.Headers.WWW_AUTHENTICATE_STRING;
import static io.undertow.util.Methods.CONNECT_STRING;
import static io.undertow.util.Methods.DELETE_STRING;
import static io.undertow.util.Methods.GET_STRING;
import static io.undertow.util.Methods.HEAD_STRING;
import static io.undertow.util.Methods.OPTIONS_STRING;
import static io.undertow.util.Methods.POST_STRING;
import static io.undertow.util.Methods.PUT_STRING;
import static io.undertow.util.Methods.TRACE_STRING;
import static io.undertow.util.Protocols.HTTP_0_9_STRING;
import static io.undertow.util.Protocols.HTTP_1_0_STRING;
import static io.undertow.util.Protocols.HTTP_1_1_STRING;

/**
 * The basic HTTP parser. The actual parser is a sub class of this class that is generated as part of
 * the build process by the {@link io.undertow.annotationprocessor.ParserGenerator} annotation processor.
 * <p/>
 * The actual processor is a state machine, that means that for common header, method, protocol values
 * it will return an interned string, rather than creating a new string for each one.
 * <p/>
 * TODO: we need to benchmark this and determine if it provides enough of a benefit to justify the additional complexity
 *
 * @author Stuart Douglas
 */
@HttpParserConfig(methods = {
        OPTIONS_STRING,
        GET_STRING,
        HEAD_STRING,
        POST_STRING,
        PUT_STRING,
        DELETE_STRING,
        TRACE_STRING,
        CONNECT_STRING},
        protocols = {
                HTTP_0_9_STRING, HTTP_1_0_STRING, HTTP_1_1_STRING
        },
        headers = {
                ACCEPT_STRING,
                ACCEPT_CHARSET_STRING,
                ACCEPT_ENCODING_STRING,
                ACCEPT_LANGUAGE_STRING,
                ACCEPT_RANGES_STRING,
                AGE_STRING,
                ALLOW_STRING,
                AUTHORIZATION_STRING,
                CACHE_CONTROL_STRING,
                COOKIE_STRING,
                CONNECTION_STRING,
                CONTENT_DISPOSITION_STRING,
                CONTENT_ENCODING_STRING,
                CONTENT_LANGUAGE_STRING,
                CONTENT_LENGTH_STRING,
                CONTENT_LOCATION_STRING,
                CONTENT_MD5_STRING,
                CONTENT_RANGE_STRING,
                CONTENT_TYPE_STRING,
                DATE_STRING,
                ETAG_STRING,
                EXPECT_STRING,
                EXPIRES_STRING,
                FROM_STRING,
                HOST_STRING,
                IF_MATCH_STRING,
                IF_MODIFIED_SINCE_STRING,
                IF_NONE_MATCH_STRING,
                IF_RANGE_STRING,
                IF_UNMODIFIED_SINCE_STRING,
                LAST_MODIFIED_STRING,
                LOCATION_STRING,
                MAX_FORWARDS_STRING,
                ORIGIN_STRING,
                PRAGMA_STRING,
                PROXY_AUTHENTICATE_STRING,
                PROXY_AUTHORIZATION_STRING,
                RANGE_STRING,
                REFERER_STRING,
                REFRESH_STRING,
                RETRY_AFTER_STRING,
                SEC_WEB_SOCKET_KEY_STRING,
                SEC_WEB_SOCKET_VERSION_STRING,
                SERVER_STRING,
                SET_COOKIE_STRING,
                SET_COOKIE2_STRING,
                STRICT_TRANSPORT_SECURITY_STRING,
                TE_STRING,
                TRAILER_STRING,
                TRANSFER_ENCODING_STRING,
                UPGRADE_STRING,
                USER_AGENT_STRING,
                VARY_STRING,
                VIA_STRING,
                WARNING_STRING,
                WWW_AUTHENTICATE_STRING})
public abstract class HttpParser {

    public static final HttpParser INSTANCE;

    static {
        try {
            final Class<?> cls = HttpParser.class.getClassLoader().loadClass(HttpParser.class.getName() + "$$generated");
            INSTANCE = (HttpParser) cls.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * This method is implemented by a generated subclass
     */
    public abstract int handle(ByteBuffer buffer, int noBytes, final ParseState currentState, final HttpServerExchange builder);


    /**
     * The parse states for parsing the path.
     */
    private static final int START = 0;
    private static final int FIRST_COLON = 1;
    private static final int FIRST_SLASH = 2;
    private static final int SECOND_SLASH = 3;
    private static final int HOST_DONE = 4;
    private static final int QUERY_PARAM_NAME = 5;
    private static final int QUERY_PARAM_VALUE = 6;

    /**
     * Parses a path value. This is called from the generated  bytecode.
     *
     * @param buffer    The buffer
     * @param remaining The number of bytes remaining
     * @param state     The current state
     * @param exchange   The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final int handlePath(ByteBuffer buffer, int remaining, ParseState state, HttpServerExchange exchange) {
        StringBuilder stringBuilder = state.stringBuilder;
        int parseState = state.parseState;
        int canonicalPathStart = state.pos;
        int queryParamPos = state.queryParamPos;
        int requestEnd = state.requestEnd;
        String nextQueryParam = state.nextQueryParam;
        if (stringBuilder == null) {
            state.stringBuilder = stringBuilder = new StringBuilder();
        }
        while (remaining > 0) {
            final char next = (char) buffer.get();
            --remaining;
            if (next == ' ' || next == '\t') {
                if (stringBuilder.length() != 0) {
                    final String path = stringBuilder.toString();
                    if (parseState < QUERY_PARAM_NAME) {
                        exchange.setRequestURI(path);
                        if (parseState < HOST_DONE) {
                            exchange.setParsedRequestPath(path);
                        } else {
                            exchange.setParsedRequestPath(path.substring(canonicalPathStart));
                        }
                        exchange.setQueryString("");
                    } else {
                        exchange.setQueryString(path.substring(requestEnd));
                    }
                    if (parseState == QUERY_PARAM_NAME) {
                        exchange.addQueryParam(stringBuilder.substring(queryParamPos), "");
                    } else if (parseState == QUERY_PARAM_VALUE) {
                        exchange.addQueryParam(nextQueryParam, stringBuilder.substring(queryParamPos));
                    }
                    state.state = ParseState.VERSION;
                    state.stringBuilder = null;
                    state.parseState = 0;
                    state.pos = 0;
                    state.nextHeader = null;
                    state.queryParamPos = 0;
                    state.requestEnd = 0;
                    return remaining;
                }
            } else {
                if (next == ':' && parseState == START) {
                    parseState = FIRST_COLON;
                } else if (next == '/' && parseState == FIRST_COLON) {
                    parseState = FIRST_SLASH;
                } else if (next == '/' && parseState == FIRST_SLASH) {
                    parseState = SECOND_SLASH;
                } else if (next == '/' && parseState == SECOND_SLASH) {
                    parseState = HOST_DONE;
                    canonicalPathStart = stringBuilder.length();
                } else if (parseState == FIRST_COLON || parseState == FIRST_SLASH) {
                    parseState = START;
                } else if (next == '?' && (parseState == START || parseState == HOST_DONE)) {
                    final String path = stringBuilder.toString();
                    exchange.setRequestURI(path);
                    if (parseState < HOST_DONE) {
                        exchange.setParsedRequestPath(path);
                    } else {
                        exchange.setParsedRequestPath(path.substring(canonicalPathStart));
                    }
                    parseState = QUERY_PARAM_NAME;
                    queryParamPos = stringBuilder.length() + 1;
                    requestEnd = queryParamPos;
                } else if (next == '=' && parseState == QUERY_PARAM_NAME) {
                    parseState = QUERY_PARAM_VALUE;
                    nextQueryParam = stringBuilder.substring(queryParamPos);
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&' && parseState == QUERY_PARAM_NAME) {
                    parseState = QUERY_PARAM_NAME;
                    exchange.addQueryParam(stringBuilder.substring(queryParamPos), "");
                    nextQueryParam = null;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&' && parseState == QUERY_PARAM_VALUE) {
                    parseState = QUERY_PARAM_NAME;
                    exchange.addQueryParam(nextQueryParam, stringBuilder.substring(queryParamPos));
                    nextQueryParam = null;
                    queryParamPos = stringBuilder.length() + 1;
                }
                stringBuilder.append(next);
            }

        }
        state.stringBuilder = stringBuilder;
        state.parseState = parseState;
        state.pos = canonicalPathStart;
        state.nextQueryParam = nextQueryParam;
        state.queryParamPos = queryParamPos;
        state.requestEnd = requestEnd;
        return remaining;
    }

    /**
     * The parse states for parsing heading values
     */
    private static final int NORMAL = 0;
    private static final int WHITESPACE = 1;
    private static final int BEGIN_LINE_END = 2;
    private static final int LINE_END = 3;
    private static final int AWAIT_DATA_END = 4;

    /**
     * Parses a header value. This is called from the generated  bytecode.
     *
     * @param buffer    The buffer
     * @param remaining The number of bytes remaining
     * @param state     The current state
     * @param builder   The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final int handleHeaderValue(ByteBuffer buffer, int remaining, ParseState state, HttpServerExchange builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
            state.parseState = 0;
        }


        int parseState = state.parseState;
        while (remaining > 0) {
            final byte next = buffer.get();
            --remaining;
            switch (parseState) {
                case NORMAL: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next == ' ' || next == '\t') {
                        parseState = WHITESPACE;
                    } else {
                        stringBuilder.append((char) next);
                    }
                    break;
                }
                case WHITESPACE: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next == ' ' || next == '\t') {
                    } else {
                        if (stringBuilder.length() > 0) {
                            stringBuilder.append(' ');
                        }
                        stringBuilder.append((char) next);
                        parseState = NORMAL;
                    }
                    break;
                }
                case LINE_END:
                case BEGIN_LINE_END: {
                    if (next == '\n' && parseState == BEGIN_LINE_END) {
                        parseState = LINE_END;
                    } else if (next == '\t' ||
                            next == ' ') {
                        //this is a continuation
                        parseState = WHITESPACE;
                    } else {
                        //we have a header
                        HttpString nextStandardHeader = state.nextHeader;
                        String headerValue = stringBuilder.toString();

                        //TODO: we need to decode this according to RFC-2047 if we have seen a =? symbol
                        builder.getRequestHeaders().add(nextStandardHeader, headerValue);

                        state.nextHeader = null;

                        state.leftOver = next;
                        state.stringBuilder = null;
                        if (next == '\r') {
                            parseState = AWAIT_DATA_END;
                        } else {
                            state.state = ParseState.HEADER;
                            state.parseState = 0;
                            return remaining;
                        }
                    }
                    break;
                }
                case AWAIT_DATA_END: {
                    state.state = ParseState.PARSE_COMPLETE;
                    return remaining;
                }
            }
        }
        //we only write to the state if we did not finish parsing
        state.parseState = parseState;
        state.stringBuilder = stringBuilder;
        return remaining;
    }

    protected int handleAfterVersion(ByteBuffer buffer, int remaining, ParseState state, HttpServerExchange builder) {
        boolean newLine = state.leftOver == '\n';
        while (remaining > 0) {
            final byte next = buffer.get();
            --remaining;
            if (newLine) {
                if (next == '\n') {
                    state.state = ParseState.PARSE_COMPLETE;
                    return remaining;
                } else {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return remaining;
                }
            } else {
                if (next == '\n') {
                    newLine = true;
                } else if (next != '\r' && next != ' ' && next != '\t') {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return remaining;
                }
            }
        }
        if (newLine) {
            state.leftOver = '\n';
        }
        return remaining;
    }

    /**
     * This is a bit of hack to enable the parser to get access to the HttpString's that are sorted
     * in the static fields of the relevant classes. This means that in most cases a HttpString comparison
     * will take the fast path == route, as they will be the same object
     *
     * @return
     */
    protected static Map<String, HttpString> httpStrings() {
        final Map<String, HttpString> results = new HashMap<String, HttpString>();
        final Class[] classs = {Headers.class, Methods.class, Protocols.class};

        for (Class<?> c : classs) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType().equals(HttpString.class)) {
                    field.setAccessible(true);
                    HttpString result = null;
                    try {
                        result = (HttpString) field.get(null);
                        results.put(result.toString(), result);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return results;

    }

}
