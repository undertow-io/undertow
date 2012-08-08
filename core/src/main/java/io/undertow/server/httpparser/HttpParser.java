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

package io.undertow.server.httpparser;

import java.nio.ByteBuffer;

import io.undertow.annotationprocessor.HttpParserConfig;

import static io.undertow.util.Headers.ACCEPT;
import static io.undertow.util.Headers.ACCEPT_CHARSET;
import static io.undertow.util.Headers.ACCEPT_ENCODING;
import static io.undertow.util.Headers.ACCEPT_LANGUAGE;
import static io.undertow.util.Headers.ACCEPT_RANGES;
import static io.undertow.util.Headers.AGE;
import static io.undertow.util.Headers.ALLOW;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.CACHE_CONTROL;
import static io.undertow.util.Headers.CONNECTION;
import static io.undertow.util.Headers.CONTENT_DISPOSITION;
import static io.undertow.util.Headers.CONTENT_ENCODING;
import static io.undertow.util.Headers.CONTENT_LANGUAGE;
import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Headers.CONTENT_LOCATION;
import static io.undertow.util.Headers.CONTENT_MD5;
import static io.undertow.util.Headers.CONTENT_RANGE;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Headers.COOKIE;
import static io.undertow.util.Headers.DATE;
import static io.undertow.util.Headers.ETAG;
import static io.undertow.util.Headers.EXPECT;
import static io.undertow.util.Headers.EXPIRES;
import static io.undertow.util.Headers.FROM;
import static io.undertow.util.Headers.HOST;
import static io.undertow.util.Headers.IF_MATCH;
import static io.undertow.util.Headers.IF_MODIFIED_SINCE;
import static io.undertow.util.Headers.IF_NONE_MATCH;
import static io.undertow.util.Headers.IF_RANGE;
import static io.undertow.util.Headers.IF_UNMODIFIED_SINCE;
import static io.undertow.util.Headers.LAST_MODIFIED;
import static io.undertow.util.Headers.LOCATION;
import static io.undertow.util.Headers.MAX_FORWARDS;
import static io.undertow.util.Headers.ORIGIN;
import static io.undertow.util.Headers.PRAGMA;
import static io.undertow.util.Headers.PROXY_AUTHENTICATE;
import static io.undertow.util.Headers.PROXY_AUTHORIZATION;
import static io.undertow.util.Headers.RANGE;
import static io.undertow.util.Headers.REFERER;
import static io.undertow.util.Headers.REFRESH;
import static io.undertow.util.Headers.RETRY_AFTER;
import static io.undertow.util.Headers.SERVER;
import static io.undertow.util.Headers.SET_COOKIE;
import static io.undertow.util.Headers.SET_COOKIE2;
import static io.undertow.util.Headers.STRICT_TRANSPORT_SECURITY;
import static io.undertow.util.Headers.TE;
import static io.undertow.util.Headers.TRAILER;
import static io.undertow.util.Headers.TRANSFER_ENCODING;
import static io.undertow.util.Headers.UPGRADE;
import static io.undertow.util.Headers.USER_AGENT;
import static io.undertow.util.Headers.VARY;
import static io.undertow.util.Headers.VIA;
import static io.undertow.util.Headers.WARNING;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.Methods.CONNECT;
import static io.undertow.util.Methods.DELETE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.HEAD;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.Methods.PUT;
import static io.undertow.util.Methods.TRACE;
import static io.undertow.util.Protocols.HTTP_0_9;
import static io.undertow.util.Protocols.HTTP_1_0;
import static io.undertow.util.Protocols.HTTP_1_1;

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
        OPTIONS,
        GET,
        HEAD,
        POST,
        PUT,
        DELETE,
        TRACE,
        CONNECT},
        protocols = {
                HTTP_0_9, HTTP_1_0, HTTP_1_1
        },
        headers = {
                ACCEPT,
                ACCEPT_CHARSET,
                ACCEPT_ENCODING,
                ACCEPT_LANGUAGE,
                ACCEPT_RANGES,
                AGE,
                ALLOW,
                AUTHORIZATION,
                CACHE_CONTROL,
                COOKIE,
                CONNECTION,
                CONTENT_DISPOSITION,
                CONTENT_ENCODING,
                CONTENT_LANGUAGE,
                CONTENT_LENGTH,
                CONTENT_LOCATION,
                CONTENT_MD5,
                CONTENT_RANGE,
                CONTENT_TYPE,
                DATE,
                ETAG,
                EXPECT,
                EXPIRES,
                FROM,
                HOST,
                IF_MATCH,
                IF_MODIFIED_SINCE,
                IF_NONE_MATCH,
                IF_RANGE,
                IF_UNMODIFIED_SINCE,
                LAST_MODIFIED,
                LOCATION,
                MAX_FORWARDS,
                ORIGIN,
                PRAGMA,
                PROXY_AUTHENTICATE,
                PROXY_AUTHORIZATION,
                RANGE,
                REFERER,
                REFRESH,
                RETRY_AFTER,
                SERVER,
                SET_COOKIE,
                SET_COOKIE2,
                STRICT_TRANSPORT_SECURITY,
                TE,
                TRAILER,
                TRANSFER_ENCODING,
                UPGRADE,
                USER_AGENT,
                VARY,
                VIA,
                WARNING,
                WWW_AUTHENTICATE})
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
    public abstract int handle(ByteBuffer buffer, int noBytes, final ParseState currentState, final HttpExchangeBuilder builder);


    /**
     * The parse states for parsing the path.
     */
    private static final int START = 0;
    private static final int FIRST_COLON = 1;
    private static final int FIRST_SLASH = 2;
    private static final int SECOND_SLASH = 3;
    private static final int HOST_DONE =  4;
    private static final int QUERY_PARAM_NAME =  5;
    private static final int QUERY_PARAM_VALUE =  6;

    /**
     * Parses a path value. This is called from the generated  bytecode.
     *
     * @param buffer    The buffer
     * @param remaining The number of bytes remaining
     * @param state     The current state
     * @param builder   The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final int handlePath(ByteBuffer buffer, int remaining, ParseState state, HttpExchangeBuilder builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        int parseState = state.parseState;
        int canonicalPathStart = state.pos;
        int queryParamPos = state.queryParamPos;
        String nextQueryParam = state.nextHeader;
        if (stringBuilder == null) {
            state.stringBuilder = stringBuilder = new StringBuilder();
        }
        while (remaining > 0) {
            final char next = (char) buffer.get();
            --remaining;
            if (next == ' ' || next == '\t') {
                if (stringBuilder.length() != 0) {
                    final String path = stringBuilder.toString();
                    builder.fullPath = path;
                    if (parseState < HOST_DONE) {
                        builder.relativePath = path;
                    } else {
                        builder.relativePath = path.substring(canonicalPathStart);
                    }
                    if(parseState == QUERY_PARAM_NAME) {
                        builder.addQueryParam(stringBuilder.substring(queryParamPos), "");
                    } else if(parseState == QUERY_PARAM_VALUE){
                        builder.addQueryParam(nextQueryParam, stringBuilder.substring(queryParamPos));
                    }
                    state.state = ParseState.VERSION;
                    state.stringBuilder = null;
                    state.parseState = 0;
                    state.pos = 0;
                    state.nextHeader = null;
                    state.queryParamPos = 0;
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
                } else if(next == '?' && (parseState == START || parseState == HOST_DONE)) {
                    parseState = QUERY_PARAM_NAME;
                    queryParamPos = stringBuilder.length() + 1;
                } else if(next == '=' && parseState == QUERY_PARAM_NAME) {
                    parseState = QUERY_PARAM_VALUE;
                    nextQueryParam = stringBuilder.substring(queryParamPos);
                    queryParamPos = stringBuilder.length() + 1;
                } else if(next == '&' && parseState == QUERY_PARAM_NAME) {
                    parseState = QUERY_PARAM_NAME;
                    builder.addQueryParam(stringBuilder.substring(queryParamPos), "");
                    nextQueryParam = null;
                    queryParamPos = stringBuilder.length() + 1;
                } else if(next == '&' && parseState == QUERY_PARAM_VALUE) {
                    parseState = QUERY_PARAM_NAME;
                    builder.addQueryParam(nextQueryParam, stringBuilder.substring(queryParamPos));
                    nextQueryParam = null;
                    queryParamPos = stringBuilder.length() + 1;
                }
                stringBuilder.append(next);
            }

        }
        state.stringBuilder = stringBuilder;
        state.parseState = parseState;
        state.pos = canonicalPathStart;
        state.nextHeader = nextQueryParam;
        state.queryParamPos = queryParamPos;
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
    final int handleHeaderValue(ByteBuffer buffer, int remaining, ParseState state, HttpExchangeBuilder builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
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
                        String nextStandardHeader = state.nextHeader;
                        builder.headers.put(nextStandardHeader, stringBuilder.toString());

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

}
