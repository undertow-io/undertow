/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package tmp.texugo.server.httpparser;

import java.nio.ByteBuffer;

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
    public abstract int handle(ByteBuffer buffer, int noBytes, final TokenState currentState, final HttpExchangeBuilder builder);


    @SuppressWarnings("unused")
    final int handlePath(ByteBuffer buffer, int remaining, TokenState state, HttpExchangeBuilder builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        if (stringBuilder == null) {
            state.stringBuilder = stringBuilder = new StringBuilder();
        }
        while (remaining > 0) {
            final char next = (char) buffer.get();
            --remaining;
            if (next == ' ' || next == '\t') {
                if (stringBuilder.length() != 0) {
                    builder.path = stringBuilder.toString();
                    state.state = TokenState.VERSION;
                    state.stringBuilder = null;
                    break;
                }
            } else {
                stringBuilder.append(next);
            }

        }
        return remaining;
    }

    private static final int EAT_WHITESPACE = 0;
    private static final int NORMAL = 1;
    private static final int BEGIN_LINE_END = 2;
    private static final int LINE_END = 3;
    private static final int AWAIT_DATA_END = 4;


    @SuppressWarnings("unused")
    final int handleHeaderValue(ByteBuffer buffer, int remaining, TokenState state, HttpExchangeBuilder builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        if (stringBuilder == null) {
            state.stringBuilder = stringBuilder = new StringBuilder();
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
                    } else {
                        stringBuilder.append((char) next);
                    }
                    break;
                }
                case EAT_WHITESPACE: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next != ' ' && next != '\t') {
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
                        stringBuilder.append(' ');
                        parseState = EAT_WHITESPACE;
                    } else {
                        //we have a header
                        String nextStandardHeader = builder.nextStandardHeader;
                        if (nextStandardHeader != null) {
                            builder.standardHeaders.put(nextStandardHeader, stringBuilder.toString());
                            builder.nextStandardHeader = null;
                        } else {
                            builder.otherHeaders.put(builder.nextOtherHeader, stringBuilder.toString());
                            builder.nextOtherHeader = null;
                        }
                        state.leftOver = next;
                        state.stringBuilder = null;
                        if(next == '\r') {
                            parseState = AWAIT_DATA_END;
                        } else {
                            state.state = TokenState.HEADER;
                            state.parseState = 0;
                            return remaining;
                        }
                    }
                    break;
                }
                case AWAIT_DATA_END: {
                    state.state = TokenState.PARSE_COMPLETE;
                    return remaining;
                }
            }
        }
        state.parseState = parseState;
        return remaining;
    }

}
