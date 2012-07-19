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

/**
 * @author Stuart Douglas
 */
@HttpParserConfig(verbs = {"GET", "POST"}, versions = {"HTTP/1.0", "HTTP/1.1"}, headers = {"Host", "Accept-Encoding"})
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
                        if (stringBuilder.length() != 0) {
                            //we have a header
                            String nextStandardHeader = builder.nextStandardHeader;
                            if (nextStandardHeader != null) {
                                builder.standardHeaders.put(nextStandardHeader, stringBuilder.toString());
                                builder.nextStandardHeader = null;
                            } else {
                                builder.otherHeaders.put(builder.nextOtherHeader, stringBuilder.toString());
                                builder.nextOtherHeader = null;
                            }
                            state.state = TokenState.HEADER;
                            state.leftOver = next;
                            state.stringBuilder = null;
                            return remaining;
                        } else {
                            state.state = TokenState.PARSE_COMPLETE;
                            return remaining;
                        }
                    }
                    break;
                }
            }
        }
        state.parseState = parseState;
        return remaining;
    }

}
