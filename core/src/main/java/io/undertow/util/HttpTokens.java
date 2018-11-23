/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

/**
 * TODO: this should not be required, delete this
 */
public class HttpTokens {

    private static final boolean[] ALLOWED_TOKEN_CHARACTERS = new boolean[256];

    static {
        for (int i = 0; i < ALLOWED_TOKEN_CHARACTERS.length; ++i) {
            if ((i >= '0' && i <= '9') ||
                    (i >= 'a' && i <= 'z') ||
                    (i >= 'A' && i <= 'Z')) {
                ALLOWED_TOKEN_CHARACTERS[i] = true;
            } else {
                switch (i) {
                    case '!':
                    case '#':
                    case '$':
                    case '%':
                    case '&':
                    case '\'':
                    case '*':
                    case '+':
                    case '-':
                    case '.':
                    case '^':
                    case '_':
                    case '`':
                    case '|':
                    case '~': {
                        ALLOWED_TOKEN_CHARACTERS[i] = true;
                        break;
                    }
                    default:
                        ALLOWED_TOKEN_CHARACTERS[i] = false;
                }
            }
        }
    }

    /**
     * Verifies that the contents of the HttpString are a valid token according to rfc7230.
     *
     * @param header The header to verify
     */
    public static void verifyToken(HttpString header) {
        int length = header.length();
        for (int i = 0; i < length; ++i) {
            byte c = header.byteAt(i);
            if (!ALLOWED_TOKEN_CHARACTERS[c]) {
                throw UndertowConnectorMessages.MESSAGES.invalidToken(c);
            }
        }
    }

    /**
     * Returns true if the token character is valid according to rfc7230
     */
    public static boolean isValidTokenCharacter(byte c) {
        return ALLOWED_TOKEN_CHARACTERS[c];
    }


}
