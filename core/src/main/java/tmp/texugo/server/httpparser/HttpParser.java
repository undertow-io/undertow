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

package tmp.texugo.server.httpparser;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import tmp.texugo.annotationprocessor.HttpParserConfig;

import static tmp.texugo.util.Headers.ACCEPT;
import static tmp.texugo.util.Headers.ACCEPT_CHARSET;
import static tmp.texugo.util.Headers.ACCEPT_ENCODING;
import static tmp.texugo.util.Headers.ACCEPT_LANGUAGE;
import static tmp.texugo.util.Headers.ACCEPT_RANGES;
import static tmp.texugo.util.Headers.AGE;
import static tmp.texugo.util.Headers.ALLOW;
import static tmp.texugo.util.Headers.AUTHORIZATION;
import static tmp.texugo.util.Headers.CACHE_CONTROL;
import static tmp.texugo.util.Headers.CONNECTION;
import static tmp.texugo.util.Headers.CONTENT_DISPOSITION;
import static tmp.texugo.util.Headers.CONTENT_ENCODING;
import static tmp.texugo.util.Headers.CONTENT_LANGUAGE;
import static tmp.texugo.util.Headers.CONTENT_LENGTH;
import static tmp.texugo.util.Headers.CONTENT_LOCATION;
import static tmp.texugo.util.Headers.CONTENT_MD5;
import static tmp.texugo.util.Headers.CONTENT_RANGE;
import static tmp.texugo.util.Headers.CONTENT_TYPE;
import static tmp.texugo.util.Headers.COOKIE;
import static tmp.texugo.util.Headers.DATE;
import static tmp.texugo.util.Headers.ETAG;
import static tmp.texugo.util.Headers.EXPECT;
import static tmp.texugo.util.Headers.EXPIRES;
import static tmp.texugo.util.Headers.FROM;
import static tmp.texugo.util.Headers.HOST;
import static tmp.texugo.util.Headers.IF_MATCH;
import static tmp.texugo.util.Headers.IF_MODIFIED_SINCE;
import static tmp.texugo.util.Headers.IF_NONE_MATCH;
import static tmp.texugo.util.Headers.IF_RANGE;
import static tmp.texugo.util.Headers.IF_UNMODIFIED_SINCE;
import static tmp.texugo.util.Headers.LAST_MODIFIED;
import static tmp.texugo.util.Headers.LOCATION;
import static tmp.texugo.util.Headers.MAX_FORWARDS;
import static tmp.texugo.util.Headers.ORIGIN;
import static tmp.texugo.util.Headers.PRAGMA;
import static tmp.texugo.util.Headers.PROXY_AUTHENTICATE;
import static tmp.texugo.util.Headers.PROXY_AUTHORIZATION;
import static tmp.texugo.util.Headers.RANGE;
import static tmp.texugo.util.Headers.REFERER;
import static tmp.texugo.util.Headers.REFRESH;
import static tmp.texugo.util.Headers.RETRY_AFTER;
import static tmp.texugo.util.Headers.SERVER;
import static tmp.texugo.util.Headers.SET_COOKIE;
import static tmp.texugo.util.Headers.SET_COOKIE2;
import static tmp.texugo.util.Headers.STRICT_TRANSPORT_SECURITY;
import static tmp.texugo.util.Headers.TE;
import static tmp.texugo.util.Headers.TRAILER;
import static tmp.texugo.util.Headers.TRANSFER_ENCODING;
import static tmp.texugo.util.Headers.UPGRADE;
import static tmp.texugo.util.Headers.USER_AGENT;
import static tmp.texugo.util.Headers.VARY;
import static tmp.texugo.util.Headers.VIA;
import static tmp.texugo.util.Headers.WARNING;
import static tmp.texugo.util.Headers.WWW_AUTHENTICATE;
import static tmp.texugo.util.Methods.CONNECT;
import static tmp.texugo.util.Methods.DELETE;
import static tmp.texugo.util.Methods.GET;
import static tmp.texugo.util.Methods.HEAD;
import static tmp.texugo.util.Methods.OPTIONS;
import static tmp.texugo.util.Methods.POST;
import static tmp.texugo.util.Methods.PUT;
import static tmp.texugo.util.Methods.TRACE;
import static tmp.texugo.util.Protocols.HTTP_0_9;
import static tmp.texugo.util.Protocols.HTTP_1_0;
import static tmp.texugo.util.Protocols.HTTP_1_1;

/**
 * The basic HTTP parser. The actual parser is a sub class of this class that is generated as part of
 * the build process by the {@link tmp.texugo.annotationprocessor.ParserGenerator} annotation processor.
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
    private static final int HOST_DONE = 3;

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
        if (stringBuilder == null) {
            state.stringBuilder = stringBuilder = new StringBuilder();
        }
        while (remaining > 0) {
            final char next = (char) buffer.get();
            --remaining;
            if (next == ' ' || next == '\t') {
                if (stringBuilder.length() != 0) {
                    final String path = stringBuilder.toString();
                    builder.path = path;
                    if (parseState != HOST_DONE) {
                        builder.canonicalPath = path;
                    } else {
                        builder.canonicalPath = path.substring(canonicalPathStart);
                    }
                    state.state = ParseState.VERSION;
                    state.stringBuilder = null;
                    state.parseState = 0;
                    state.pos = 0;
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
                }
                stringBuilder.append(next);
            }

        }
        state.stringBuilder = stringBuilder;
        state.parseState = parseState;
        state.pos = canonicalPathStart;
        return remaining;
    }

    /**
     * The parse states for parsing heading values
     */
    private static final int NORMAL = 0;
    private static final int BEGIN_LINE_END = 1;
    private static final int LINE_END = 2;
    private static final int AWAIT_DATA_END = 3;


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
        List<String> nextHeaderValues = state.nextHeaderValues;
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
            nextHeaderValues = new ArrayList<String>();
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
                        if (stringBuilder.length() != 0) {
                            nextHeaderValues.add(stringBuilder.toString());
                            stringBuilder = new StringBuilder();
                        }
                    } else {
                        stringBuilder.append((char) next);
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
                        if (stringBuilder.length() != 0) {
                            nextHeaderValues.add(stringBuilder.toString());
                            stringBuilder = new StringBuilder();
                        }
                        parseState = NORMAL;
                    } else {
                        //we have a header
                        String nextStandardHeader = state.nextHeader;
                        if (stringBuilder.length() != 0) {
                            nextHeaderValues.add(stringBuilder.toString());
                        }
                        if (nextHeaderValues.size() == 1) {
                            builder.headers.put(nextStandardHeader, nextHeaderValues.get(0));
                        } else {
                            builder.headers.putAll(nextStandardHeader, nextHeaderValues);
                        }
                        state.nextHeader = null;
                        state.nextHeaderValues = null;

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
        state.nextHeaderValues = nextHeaderValues;
        return remaining;
    }

}
