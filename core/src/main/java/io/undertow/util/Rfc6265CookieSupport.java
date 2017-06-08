/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.undertow.util;

import io.undertow.UndertowMessages;
import java.util.BitSet;

/**
 * Class that contains utility methods for dealing with RFC6265 Cookies.
 *
 */
public final class Rfc6265CookieSupport {

    private static final BitSet domainValid = new BitSet(128);

    static {
        for (char c = '0'; c <= '9'; c++) {
            domainValid.set(c);
        }
        for (char c = 'a'; c <= 'z'; c++) {
            domainValid.set(c);
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            domainValid.set(c);
        }
        domainValid.set('.');
        domainValid.set('-');
    }

    public static void validateCookieValue(String value) {
        int start = 0;
        int end = value.length();

        if (end > 1 && value.charAt(0) == '"' && value.charAt(end - 1) == '"') {
            start = 1;
            end--;
        }

        char[] chars = value.toCharArray();
        for (int i = start; i < end; i++) {
            char c = chars[i];
            if (c < 0x21 || c == 0x22 || c == 0x2c || c == 0x3b || c == 0x5c || c == 0x7f) {
                throw UndertowMessages.MESSAGES.invalidCookieValue(Integer.toString(c));
            }
        }
    }

    public static void validateDomain(String domain) {
        int i = 0;
        int prev = -1;
        int cur = -1;
        char[] chars = domain.toCharArray();
        while (i < chars.length) {
            prev = cur;
            cur = chars[i];
            if (!domainValid.get(cur)) {
                throw UndertowMessages.MESSAGES.invalidCookieDomain(domain);
            }
            // labels must start with a letter or number
            if ((prev == '.' || prev == -1) && (cur == '.' || cur == '-')) {
                throw UndertowMessages.MESSAGES.invalidCookieDomain(domain);
            }
            // labels must end with a letter or number
            if (prev == '-' && cur == '.') {
                throw UndertowMessages.MESSAGES.invalidCookieDomain(domain);
            }
            i++;
        }
        // domain must end with a label
        if (cur == '.' || cur == '-') {
            throw UndertowMessages.MESSAGES.invalidCookieDomain(domain);
        }
    }

    public static void validatePath(String path) {
        char[] chars = path.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            if (ch < 0x20 || ch > 0x7E || ch == ';') {
                throw UndertowMessages.MESSAGES.invalidCookiePath(path);
            }
        }
    }
}
