/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.annotationprocessor.HttpParserConfig;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import org.xnio.OptionMap;

import static io.undertow.util.Headers.ACCEPT_CHARSET_STRING;
import static io.undertow.util.Headers.ACCEPT_ENCODING_STRING;
import static io.undertow.util.Headers.ACCEPT_LANGUAGE_STRING;
import static io.undertow.util.Headers.ACCEPT_RANGES_STRING;
import static io.undertow.util.Headers.ACCEPT_STRING;
import static io.undertow.util.Headers.AUTHORIZATION_STRING;
import static io.undertow.util.Headers.CACHE_CONTROL_STRING;
import static io.undertow.util.Headers.CONNECTION_STRING;
import static io.undertow.util.Headers.CONTENT_LENGTH_STRING;
import static io.undertow.util.Headers.CONTENT_TYPE_STRING;
import static io.undertow.util.Headers.COOKIE_STRING;
import static io.undertow.util.Headers.EXPECT_STRING;
import static io.undertow.util.Headers.FROM_STRING;
import static io.undertow.util.Headers.HOST_STRING;
import static io.undertow.util.Headers.IF_MATCH_STRING;
import static io.undertow.util.Headers.IF_MODIFIED_SINCE_STRING;
import static io.undertow.util.Headers.IF_NONE_MATCH_STRING;
import static io.undertow.util.Headers.IF_RANGE_STRING;
import static io.undertow.util.Headers.IF_UNMODIFIED_SINCE_STRING;
import static io.undertow.util.Headers.MAX_FORWARDS_STRING;
import static io.undertow.util.Headers.ORIGIN_STRING;
import static io.undertow.util.Headers.PRAGMA_STRING;
import static io.undertow.util.Headers.PROXY_AUTHORIZATION_STRING;
import static io.undertow.util.Headers.RANGE_STRING;
import static io.undertow.util.Headers.REFERER_STRING;
import static io.undertow.util.Headers.REFRESH_STRING;
import static io.undertow.util.Headers.SEC_WEB_SOCKET_KEY_STRING;
import static io.undertow.util.Headers.SEC_WEB_SOCKET_VERSION_STRING;
import static io.undertow.util.Headers.SERVER_STRING;
import static io.undertow.util.Headers.STRICT_TRANSPORT_SECURITY_STRING;
import static io.undertow.util.Headers.TRAILER_STRING;
import static io.undertow.util.Headers.TRANSFER_ENCODING_STRING;
import static io.undertow.util.Headers.UPGRADE_STRING;
import static io.undertow.util.Headers.USER_AGENT_STRING;
import static io.undertow.util.Headers.VIA_STRING;
import static io.undertow.util.Headers.WARNING_STRING;
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
 * the build process by the {@link io.undertow.annotationprocessor.AbstractParserGenerator} annotation processor.
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
                AUTHORIZATION_STRING,
                CACHE_CONTROL_STRING,
                COOKIE_STRING,
                CONNECTION_STRING,
                CONTENT_LENGTH_STRING,
                CONTENT_TYPE_STRING,
                EXPECT_STRING,
                FROM_STRING,
                HOST_STRING,
                IF_MATCH_STRING,
                IF_MODIFIED_SINCE_STRING,
                IF_NONE_MATCH_STRING,
                IF_RANGE_STRING,
                IF_UNMODIFIED_SINCE_STRING,
                MAX_FORWARDS_STRING,
                ORIGIN_STRING,
                PRAGMA_STRING,
                PROXY_AUTHORIZATION_STRING,
                RANGE_STRING,
                REFERER_STRING,
                REFRESH_STRING,
                SEC_WEB_SOCKET_KEY_STRING,
                SEC_WEB_SOCKET_VERSION_STRING,
                SERVER_STRING,
                STRICT_TRANSPORT_SECURITY_STRING,
                TRAILER_STRING,
                TRANSFER_ENCODING_STRING,
                UPGRADE_STRING,
                USER_AGENT_STRING,
                VIA_STRING,
                WARNING_STRING
        })
public abstract class HttpRequestParser {

    //constants used for UTF-8 decoding
    private static final int UTF8_ACCEPT = 0;

    private static final byte[] TYPES = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8,
            8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8,
            8, 8, 8, 8, 8, 8};

    private static final byte[] STATES = {0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12,
            12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12,
            12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
            12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 36,
            12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12,
            12, 12, 12, 12, 12, 12};

    private final int maxParameters;
    private final int maxHeaders;

    public HttpRequestParser(OptionMap options) {
        maxParameters = options.get(UndertowOptions.MAX_PARAMETERS, 1000);
        maxHeaders = options.get(UndertowOptions.MAX_HEADERS, 200);
    }

    public static final HttpRequestParser instance(final OptionMap options) {
        try {
            final Class<?> cls = HttpRequestParser.class.getClassLoader().loadClass(HttpRequestParser.class.getName() + "$$generated");

            Constructor<?> ctor = cls.getConstructor(OptionMap.class);
            return (HttpRequestParser) ctor.newInstance(options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void handle(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) {
        if (currentState.state == ParseState.VERB) {
            //fast path, we assume that it will parse fully so we avoid all the if statements
            handleHttpVerb(buffer, currentState, builder);
            handlePath(buffer, currentState, builder);
            handleHttpVersion(buffer, currentState, builder);
            handleAfterVersion(buffer, currentState, builder);
            while (currentState.state != ParseState.PARSE_COMPLETE && buffer.hasRemaining()) {
                handleHeader(buffer, currentState, builder);
                if (currentState.state == ParseState.HEADER_VALUE) {
                    handleHeaderValue(buffer, currentState, builder);
                }
            }
            return;
        }
        if (currentState.state == ParseState.PATH) {
            handlePath(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.QUERY_PARAMETERS) {
            handleQueryParameters(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.VERSION) {
            handleHttpVersion(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }
        if (currentState.state == ParseState.AFTER_VERSION) {
            handleAfterVersion(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }
        while (currentState.state != ParseState.PARSE_COMPLETE) {
            if (currentState.state == ParseState.HEADER) {
                handleHeader(buffer, currentState, builder);
                if (!buffer.hasRemaining()) {
                    return;
                }
            }
            if (currentState.state == ParseState.HEADER_VALUE) {
                handleHeaderValue(buffer, currentState, builder);
                if (!buffer.hasRemaining()) {
                    return;
                }
            }
        }
    }


    abstract void handleHttpVerb(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder);

    abstract void handleHttpVersion(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder);

    abstract void handleHeader(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder);

    /**
     * The parse states for parsing the path.
     */
    private static final int START = 0;
    private static final int FIRST_COLON = 1;
    private static final int FIRST_SLASH = 2;
    private static final int SECOND_SLASH = 3;
    private static final int HOST_DONE = 4;

    /**
     * Parses a path value
     *
     * @param buffer   The buffer
     * @param state    The current state
     * @param exchange The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handlePath(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) {
        StringBuilder stringBuilder = state.stringBuilder;
        int parseState = state.parseState;
        int canonicalPathStart = state.pos;
        int urlDecodeState = state.urlDecodeState;
        int urlDecodeCurrentByte = (urlDecodeState & 0xFF00) >> 8;
        urlDecodeState &= 0xFF;
        int urlDecodeCodePoint = state.urlDecodeCodePoint;

        while (buffer.hasRemaining()) {
            final char next = (char) buffer.get();
            if (next == ' ' || next == '\t') {
                if (stringBuilder.length() != 0) {
                    final String path = stringBuilder.toString();
                    exchange.setRequestURI(path);
                    if (parseState < HOST_DONE) {
                        exchange.setParsedRequestPath(path);
                    } else {
                        exchange.setParsedRequestPath(path.substring(canonicalPathStart));
                    }
                    exchange.setQueryString("");
                    state.state = ParseState.VERSION;
                    state.stringBuilder.setLength(0);
                    state.parseState = 0;
                    state.pos = 0;
                    state.urlDecodeState = 0;
                    state.urlDecodeCodePoint = 0;
                    return;
                }
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
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
                    state.state = ParseState.QUERY_PARAMETERS;
                    state.stringBuilder.setLength(0);
                    state.parseState = 0;
                    state.pos = 0;
                    state.urlDecodeState = 0;
                    state.urlDecodeCodePoint = 0;
                    handleQueryParameters(buffer, state, exchange);
                    return;
                }
                if (urlDecodeCurrentByte != 0) {
                    if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f') || (next >= 'A' && next <= 'F')) {
                        if (urlDecodeCurrentByte == -1) {
                            urlDecodeCurrentByte = Integer.parseInt("" + next, 16);
                        } else {
                            urlDecodeCurrentByte <<= 4;
                            urlDecodeCurrentByte += Integer.parseInt("" + next, 16);
                            byte type = TYPES[urlDecodeCurrentByte & 0xFF];

                            urlDecodeCodePoint = urlDecodeState != UTF8_ACCEPT ? urlDecodeCurrentByte & 0x3f | urlDecodeCodePoint << 6 : 0xff >> type & urlDecodeCurrentByte;

                            urlDecodeState = STATES[urlDecodeState + type];

                            if (urlDecodeState == UTF8_ACCEPT) {
                                for (char c : Character.toChars(urlDecodeCodePoint)) {
                                    stringBuilder.append(c);
                                }
                                urlDecodeCurrentByte = 0;
                            } else {
                                urlDecodeCurrentByte = -1;
                            }
                        }
                    } else if (next != '%') {
                        throw UndertowMessages.MESSAGES.failedToParsePath();
                    }
                } else if (next == '%') {
                    urlDecodeCurrentByte = -1;
                    urlDecodeCodePoint = 0;
                    urlDecodeState = 0;
                } else if (next == '+') {
                    stringBuilder.append(' ');
                } else {
                    stringBuilder.append(next);
                }
            }

        }
        state.parseState = parseState;
        state.pos = canonicalPathStart;
        state.urlDecodeState = urlDecodeState & urlDecodeCurrentByte;
        state.urlDecodeCodePoint = urlDecodeCodePoint;
    }


    /**
     * Parses a path value
     *
     * @param buffer   The buffer
     * @param state    The current state
     * @param exchange The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handleQueryParameters(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) {
        StringBuilder stringBuilder = state.stringBuilder;
        int queryParamPos = state.pos;
        int mapCount = state.mapCount;
        int urlDecodeState = state.urlDecodeState;
        int urlDecodeCurrentByte = (urlDecodeState & 0xFF00) >> 8;
        urlDecodeState &= 0xFF;
        int urlDecodeCodePoint = state.urlDecodeCodePoint;
        String nextQueryParam = state.nextQueryParam;

        while (buffer.hasRemaining()) {
            final char next = (char) buffer.get();
            if (next == ' ' || next == '\t') {
                final String queryString = stringBuilder.toString();
                exchange.setQueryString(queryString);
                if (nextQueryParam == null) {
                    if (queryParamPos != stringBuilder.length()) {
                        exchange.addQueryParam(stringBuilder.substring(queryParamPos), "");
                    }
                } else {
                    exchange.addQueryParam(nextQueryParam, stringBuilder.substring(queryParamPos));
                }
                state.state = ParseState.VERSION;
                state.stringBuilder.setLength(0);
                state.pos = 0;
                state.nextQueryParam = null;
                state.urlDecodeCodePoint = 0;
                state.urlDecodeState = 0;
                state.mapCount = 0;
                return;
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else {
                if (next == '=' && nextQueryParam == null) {
                    nextQueryParam = stringBuilder.substring(queryParamPos);
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&' && nextQueryParam == null) {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }
                    exchange.addQueryParam(stringBuilder.substring(queryParamPos), "");
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&') {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }

                    exchange.addQueryParam(nextQueryParam, stringBuilder.substring(queryParamPos));
                    queryParamPos = stringBuilder.length() + 1;
                    nextQueryParam = null;
                }
                if (urlDecodeCurrentByte != 0) {
                    if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f') || (next >= 'A' && next <= 'F')) {
                        if (urlDecodeCurrentByte == -1) {
                            urlDecodeCurrentByte = Integer.parseInt("" + next, 16);
                        } else {
                            urlDecodeCurrentByte <<= 4;
                            urlDecodeCurrentByte += Integer.parseInt("" + next, 16);
                            byte type = TYPES[urlDecodeCurrentByte & 0xFF];

                            urlDecodeCodePoint = urlDecodeState != UTF8_ACCEPT ? urlDecodeCurrentByte & 0x3f | urlDecodeCodePoint << 6 : 0xff >> type & urlDecodeCurrentByte;

                            urlDecodeState = STATES[urlDecodeState + type];

                            if (urlDecodeState == UTF8_ACCEPT) {
                                for (char c : Character.toChars(urlDecodeCodePoint)) {
                                    stringBuilder.append(c);
                                }
                                urlDecodeCurrentByte = 0;
                            } else {
                                urlDecodeCurrentByte = -1;
                            }
                        }
                    } else if (next != '%') {
                        throw UndertowMessages.MESSAGES.failedToParsePath();
                    }
                } else if (next == '%') {
                    urlDecodeCurrentByte = -1;
                    urlDecodeCodePoint = 0;
                    urlDecodeState = 0;
                } else if (next == '+') {
                    stringBuilder.append(' ');
                } else {
                    stringBuilder.append(next);
                }
            }

        }
        state.pos = queryParamPos;
        state.nextQueryParam = nextQueryParam;
        state.urlDecodeState = urlDecodeState & urlDecodeCurrentByte;
        state.urlDecodeCodePoint = urlDecodeCodePoint;
        state.mapCount = 0;
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
     * @param buffer  The buffer
     * @param state   The current state
     * @param builder The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handleHeaderValue(ByteBuffer buffer, ParseState state, HttpServerExchange builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
            state.parseState = 0;

            if (state.mapCount++ > maxHeaders) {
                throw UndertowMessages.MESSAGES.tooManyHeaders(maxHeaders);
            }
        }


        int parseState = state.parseState;
        while (buffer.hasRemaining() && parseState == NORMAL) {
            final byte next = buffer.get();
            if (next == '\r') {
                parseState = BEGIN_LINE_END;
            } else if (next == '\n') {
                parseState = LINE_END;
            } else if (next == ' ' || next == '\t') {
                parseState = WHITESPACE;
            } else {
                stringBuilder.append((char) next);
            }
        }

        while (buffer.hasRemaining()) {
            final byte next = buffer.get();
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
                        state.stringBuilder.setLength(0);
                        if (next == '\r') {
                            parseState = AWAIT_DATA_END;
                        } else {
                            state.state = ParseState.HEADER;
                            state.parseState = 0;
                            return;
                        }
                    }
                    break;
                }
                case AWAIT_DATA_END: {
                    state.state = ParseState.PARSE_COMPLETE;
                    return;
                }
            }
        }
        //we only write to the state if we did not finish parsing
        state.parseState = parseState;
        return;
    }

    protected void handleAfterVersion(ByteBuffer buffer, ParseState state, HttpServerExchange builder) {
        boolean newLine = state.leftOver == '\n';
        while (buffer.hasRemaining()) {
            final byte next = buffer.get();
            if (newLine) {
                if (next == '\n') {
                    state.state = ParseState.PARSE_COMPLETE;
                    return;
                } else {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return;
                }
            } else {
                if (next == '\n') {
                    newLine = true;
                } else if (next != '\r' && next != ' ' && next != '\t') {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return;
                }
            }
        }
        if (newLine) {
            state.leftOver = '\n';
        }
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
