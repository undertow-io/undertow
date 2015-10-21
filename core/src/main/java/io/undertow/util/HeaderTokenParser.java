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

import static io.undertow.UndertowMessages.MESSAGES;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility to parse the tokens contained within a HTTP header.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class HeaderTokenParser<E extends HeaderToken> {

    private static final char EQUALS = '=';
    private static final char COMMA = ',';
    private static final char QUOTE = '"';
    private static final char ESCAPE = '\\';

    private final Map<String, E> expectedTokens;

    public HeaderTokenParser(final Map<String, E> expectedTokens) {
        this.expectedTokens = expectedTokens;
    }

    public Map<E, String> parseHeader(final String header) {
        char[] headerChars = header.toCharArray();

        // The LinkedHashMap is used so that the parameter order can also be retained.
        Map<E, String> response = new LinkedHashMap<>();

        SearchingFor searchingFor = SearchingFor.START_OF_NAME;
        int nameStart = 0;
        E currentToken = null;
        int valueStart = 0;

        int escapeCount = 0;
        boolean containsEscapes = false;

        for (int i = 0; i < headerChars.length; i++) {
            switch (searchingFor) {
                case START_OF_NAME:
                    // Eliminate any white space before the name of the parameter.
                    if (headerChars[i] != COMMA && !Character.isWhitespace(headerChars[i])) {
                        nameStart = i;
                        searchingFor = SearchingFor.EQUALS_SIGN;
                    }
                    break;
                case EQUALS_SIGN:
                    if (headerChars[i] == EQUALS) {
                        String paramName = String.valueOf(headerChars, nameStart, i - nameStart);
                        currentToken = expectedTokens.get(paramName);
                        if (currentToken == null) {
                            throw MESSAGES.unexpectedTokenInHeader(paramName);
                        }
                        searchingFor = SearchingFor.START_OF_VALUE;
                    }
                    break;
                case START_OF_VALUE:
                    if (!Character.isWhitespace(headerChars[i])) {
                        if (headerChars[i] == QUOTE && currentToken.isAllowQuoted()) {
                            valueStart = i + 1;
                            searchingFor = SearchingFor.LAST_QUOTE;
                        } else {
                            valueStart = i;
                            searchingFor = SearchingFor.END_OF_VALUE;
                        }
                    }
                    break;
                case LAST_QUOTE:
                    if (headerChars[i] == ESCAPE) {
                        escapeCount++;
                        containsEscapes = true;
                    } else if (headerChars[i] == QUOTE && (escapeCount % 2 == 0)) {
                        String value = String.valueOf(headerChars, valueStart, i - valueStart);
                        if(containsEscapes) {
                            StringBuilder sb = new StringBuilder();
                            boolean lastEscape = false;
                            for(int j = 0; j < value.length(); ++j) {
                                char c = value.charAt(j);
                                if(c == ESCAPE && !lastEscape) {
                                    lastEscape = true;
                                } else {
                                    lastEscape = false;
                                    sb.append(c);
                                }
                            }
                            value = sb.toString();
                            containsEscapes = false;
                        }
                        response.put(currentToken, value);

                        searchingFor = SearchingFor.START_OF_NAME;
                        escapeCount = 0;
                    } else {
                        escapeCount = 0;
                    }
                    break;
                case END_OF_VALUE:
                    if (headerChars[i] == COMMA || Character.isWhitespace(headerChars[i])) {
                        String value = String.valueOf(headerChars, valueStart, i - valueStart);
                        response.put(currentToken, value);

                        searchingFor = SearchingFor.START_OF_NAME;
                    }
                    break;
            }
        }

        if (searchingFor == SearchingFor.END_OF_VALUE) {
            // Special case where we reached the end of the array containing the header values.
            String value = String.valueOf(headerChars, valueStart, headerChars.length - valueStart);
            response.put(currentToken, value);
        } else if (searchingFor != SearchingFor.START_OF_NAME) {
            // Somehow we are still in the middle of searching for a current value.
            throw MESSAGES.invalidHeader();
        }

        return response;
    }

    enum SearchingFor {
        START_OF_NAME, EQUALS_SIGN, START_OF_VALUE, LAST_QUOTE, END_OF_VALUE;
    }

}
