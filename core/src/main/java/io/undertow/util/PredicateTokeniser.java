/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.util;

import io.undertow.UndertowMessages;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tokeniser that is re-used by the predicate and handler parsers, as well as the combined predicated
 * handlers parser.
 *
 * @author Stuart Douglas
 */
public class PredicateTokeniser {


    public static Deque<Token> tokenize(final String string) {
        char currentStringDelim = 0;
        boolean inVariable = false;

        int pos = 0;
        StringBuilder current = new StringBuilder();
        Deque<Token> ret = new ArrayDeque<>();
        while (pos < string.length()) {
            char c = string.charAt(pos);
            if (currentStringDelim != 0) {
                if (c == currentStringDelim && current.charAt(current.length() - 1) != '\\') {
                    ret.add(new Token(current.toString(), pos));
                    current.setLength(0);
                    currentStringDelim = 0;
                } else if(c == '\n') {
                    ret.add(new Token(current.toString(), pos));
                    current.setLength(0);
                    currentStringDelim = 0;
                    ret.add(new Token("\n", pos));
                } else {
                    current.append(c);
                }
            } else {
                switch (c) {
                    case ' ':
                    case '\t': {
                        if (current.length() != 0) {
                            ret.add(new Token(current.toString(), pos));
                            current.setLength(0);
                        }
                        break;
                    }
                    case '\n': {
                        if (current.length() != 0) {
                            ret.add(new Token(current.toString(), pos));
                            current.setLength(0);
                        }
                        ret.add(new Token("\n", pos));
                        break;
                    }
                    case '(':
                    case ')':
                    case ',':
                    case '=':
                    case '[':
                    case ']':
                    case '{':
                    case '}': {
                        if (inVariable) {
                            current.append(c);
                            if (c == '}') {
                                inVariable = false;
                            }
                        } else {
                            if (current.length() != 0) {
                                ret.add(new Token(current.toString(), pos));
                                current.setLength(0);
                            }
                            ret.add(new Token("" + c, pos));
                        }
                        break;
                    }
                    case '"':
                    case '\'': {
                        if (current.length() != 0) {
                            throw error(string, pos, "Unexpected token");
                        }
                        currentStringDelim = c;
                        break;
                    }
                    case '%':
                    case '$': {
                        current.append(c);
                        if (string.charAt(pos + 1) == '{') {
                            inVariable = true;
                        }
                        break;
                    }
                    case '-':
                        if (inVariable) {
                            current.append(c);
                        } else {
                            if (pos != string.length() && string.charAt(pos + 1) == '>') {
                                pos++;
                                if (current.length() != 0) {
                                    ret.add(new Token(current.toString(), pos));
                                    current.setLength(0);
                                }
                                ret.add(new Token("->", pos));
                            } else {
                                current.append(c);
                            }
                        }
                        break;
                    default:
                        current.append(c);
                }
            }
            ++pos;
        }
        if (current.length() > 0) {
            ret.add(new Token(current.toString(), string.length()));
        }
        return ret;
    }

    public static final class Token {
        private final String token;
        private final int position;

        public Token(final String token, final int position) {
            this.token = token;
            this.position = position;
        }

        public String getToken() {
            return token;
        }

        public int getPosition() {
            return position;
        }

        @Override
        public String toString() {
            return token + " <" + position + ">";
        }
    }


    public static IllegalStateException error(final String string, int pos, String reason) {
        StringBuilder b = new StringBuilder();
        int linePos = 0;
        for(int i = 0; i < string.length(); ++i) {
            if(string.charAt(i) == '\n') {
                if(i >= pos) {
                    //truncate the string at the error line
                    break;
                } else {
                    linePos = 0;
                }
            } else if(i < pos) {
                linePos++;
            }
            b.append(string.charAt(i));
        }
        b.append('\n');
        for (int i = 0; i < linePos; ++i) {
            b.append(' ');
        }
        b.append('^');
        throw UndertowMessages.MESSAGES.errorParsingPredicateString(reason, b.toString());
    }

}
