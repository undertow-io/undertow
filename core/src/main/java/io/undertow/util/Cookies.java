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

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Class that contains utility methods for dealing with cookies.
 *
 * @author Stuart Douglas
 * @author Andre Dietisheim
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class Cookies {

    public static final String DOMAIN = "$Domain";
    public static final String VERSION = "$Version";
    public static final String PATH = "$Path";


    /**
     * Parses a "Set-Cookie:" response header value into its cookie representation. The header value is parsed according to the
     * syntax that's defined in RFC2109:
     *
     * <pre>
     * <code>
     *  set-cookie      =       "Set-Cookie:" cookies
     *   cookies         =       1#cookie
     *   cookie          =       NAME "=" VALUE *(";" cookie-av)
     *   NAME            =       attr
     *   VALUE           =       value
     *   cookie-av       =       "Comment" "=" value
     *                   |       "Domain" "=" value
     *                   |       "Max-Age" "=" value
     *                   |       "Path" "=" value
     *                   |       "Secure"
     *                   |       "Version" "=" 1*DIGIT
     *
     * </code>
     * </pre>
     *
     * @param headerValue The header value
     * @return The cookie
     *
     * @see Cookie
     * @see <a href="http://tools.ietf.org/search/rfc2109">rfc2109</a>
     */
    public static Cookie parseSetCookieHeader(final String headerValue) {

        String key = null;
        CookieImpl cookie = null;
        int state = 0;
        int current = 0;
        for (int i = 0; i < headerValue.length(); ++i) {
            char c = headerValue.charAt(i);
            switch (state) {
                case 0: {
                    //reading key
                    if (c == '=') {
                        key = headerValue.substring(current, i);
                        current = i + 1;
                        state = 1;
                    } else if ((c == ';' || c == ' ') && current == i) {
                        current++;
                    } else if (c == ';') {
                        if (cookie == null) {
                            throw UndertowMessages.MESSAGES.couldNotParseCookie(headerValue);
                        } else {
                            handleValue(cookie, headerValue.substring(current, i), null);
                        }
                        current = i + 1;
                    }
                    break;
                }
                case 1: {
                    if (c == ';') {
                        if (cookie == null) {
                            cookie = new CookieImpl(key, headerValue.substring(current, i));
                        } else {
                            handleValue(cookie, key, headerValue.substring(current, i));
                        }
                        state = 0;
                        current = i + 1;
                        key = null;
                    } else if (c == '"' && current == i) {
                        current++;
                        state = 2;
                    }
                    break;
                }
                case 2: {
                    if (c == '"') {
                        if (cookie == null) {
                            cookie = new CookieImpl(key, headerValue.substring(current, i));
                        } else {
                            handleValue(cookie, key, headerValue.substring(current, i));
                        }
                        state = 0;
                        current = i + 1;
                        key = null;
                    }
                    break;
                }
            }
        }
        if (key == null) {
            if (current != headerValue.length()) {
                handleValue(cookie, headerValue.substring(current, headerValue.length()), null);
            }
        } else {
            if (current != headerValue.length()) {
                if(cookie == null) {
                    cookie = new CookieImpl(key, headerValue.substring(current, headerValue.length()));
                } else {
                    handleValue(cookie, key, headerValue.substring(current, headerValue.length()));
                }
            } else {
                handleValue(cookie, key, null);
            }
        }

        return cookie;
    }

    private static void handleValue(CookieImpl cookie, String key, String value) {
        if (key == null) {
            return;
        }
        if (key.equalsIgnoreCase("path")) {
            cookie.setPath(value);
        } else if (key.equalsIgnoreCase("domain")) {
            cookie.setDomain(value);
        } else if (key.equalsIgnoreCase("max-age")) {
            cookie.setMaxAge(Integer.parseInt(value));
        } else if (key.equalsIgnoreCase("expires")) {
            cookie.setExpires(DateUtils.parseDate(value));
        } else if (key.equalsIgnoreCase("discard")) {
            cookie.setDiscard(true);
        } else if (key.equalsIgnoreCase("secure")) {
            cookie.setSecure(true);
        } else if (key.equalsIgnoreCase("httpOnly")) {
            cookie.setHttpOnly(true);
        } else if (key.equalsIgnoreCase("version")) {
            cookie.setVersion(Integer.parseInt(value));
        } else if (key.equalsIgnoreCase("comment")) {
            cookie.setComment(value);
        } else if (key.equalsIgnoreCase("samesite")) {
            cookie.setSameSite(true);
            cookie.setSameSiteMode(value);
        }
        //otherwise ignore this key-value pair
    }

    /**
    /**
     * Parses the cookies from a list of "Cookie:" header values. The cookie header values are parsed according to RFC2109 that
     * defines the following syntax:
     *
     * <pre>
     * <code>
     * cookie          =  "Cookie:" cookie-version
     *                    1*((";" | ",") cookie-value)
     * cookie-value    =  NAME "=" VALUE [";" path] [";" domain]
     * cookie-version  =  "$Version" "=" value
     * NAME            =  attr
     * VALUE           =  value
     * path            =  "$Path" "=" value
     * domain          =  "$Domain" "=" value
     * </code>
     * </pre>
     *
     * @param maxCookies The maximum number of cookies. Used to prevent hash collision attacks
     * @param allowEqualInValue if true equal characters are allowed in cookie values
     * @param cookies The cookie values to parse
     * @return A pared cookie map
     *
     * @see Cookie
     * @see <a href="http://tools.ietf.org/search/rfc2109">rfc2109</a>
     * @deprecated use {@link #parseRequestCookies(int, boolean, List, Set)} instead
     */
    @Deprecated(since="2.3.0", forRemoval=true)
    public static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies) {
        return parseRequestCookies(maxCookies, allowEqualInValue, cookies, LegacyCookieSupport.COMMA_IS_SEPARATOR);
    }

    public static void parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, Set<Cookie> parsedCookies) {
        parseRequestCookies(maxCookies, allowEqualInValue, cookies, parsedCookies, LegacyCookieSupport.COMMA_IS_SEPARATOR);
    }

    @Deprecated
    static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, boolean commaIsSeperator) {
        return parseRequestCookies(maxCookies, allowEqualInValue, cookies, commaIsSeperator, LegacyCookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0);
    }

    static void parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, Set<Cookie> parsedCookies, boolean commaIsSeperator) {
        parseRequestCookies(maxCookies, allowEqualInValue, cookies, parsedCookies, commaIsSeperator, LegacyCookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0);
    }

    static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, boolean commaIsSeperator, boolean allowHttpSepartorsV0) {
        if (cookies == null) {
            return new TreeMap<>();
        }
        final Set<Cookie> parsedCookies = new HashSet<>();
        for (String cookie : cookies) {
            parseCookie(cookie, parsedCookies, maxCookies, allowEqualInValue, commaIsSeperator, allowHttpSepartorsV0);
        }

        final Map<String, Cookie> retVal = new TreeMap<>();
        for (Cookie cookie : parsedCookies) {
            retVal.put(cookie.getName(), cookie);
        }
        return retVal;
    }

    static void parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, Set<Cookie> parsedCookies, boolean commaIsSeperator, boolean allowHttpSepartorsV0) {
        if (cookies != null) {
            for (String cookie : cookies) {
                parseCookie(cookie, parsedCookies, maxCookies, allowEqualInValue, commaIsSeperator, allowHttpSepartorsV0);
            }
        }
    }

    private static void parseCookie(final String cookie, final Set<Cookie> parsedCookies, int maxCookies, boolean allowEqualInValue, boolean commaIsSeperator, boolean allowHttpSepartorsV0) {
        int state = 0;
        String name = null;
        int start = 0;
        boolean containsEscapedQuotes = false;
        int cookieCount = parsedCookies.size();
        final Map<String, String> cookies = new HashMap<>();
        final Map<String, String> additional = new HashMap<>();
        for (int i = 0; i < cookie.length(); ++i) {
            char c = cookie.charAt(i);
            switch (state) {
                case 0: {
                    //eat leading whitespace
                    if (c == ' ' || c == '\t' || c == ';') {
                        start = i + 1;
                        break;
                    }
                    state = 1;
                    //fall through
                }
                case 1: {
                    //extract key
                    if (c == '=') {
                        name = cookie.substring(start, i);
                        start = i + 1;
                        state = 2;
                    } else if (c == ';' || (commaIsSeperator && c == ',')) {
                        if(name != null) {
                            cookieCount = createCookie(name, cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                        } else if(UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.trace("Ignoring invalid cookies in header " + cookie);
                        }
                        state = 0;
                        start = i + 1;
                    }
                    break;
                }
                case 2: {
                    //extract value
                    if (c == ';' || (commaIsSeperator && c == ',')) {
                        cookieCount = createCookie(name, cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                        state = 0;
                        start = i + 1;
                    } else if (c == '"' && start == i) { //only process the " if it is the first character
                        containsEscapedQuotes = false;
                        state = 3;
                        start = i + 1;
                    } else if (c == '=') {
                        if (!allowEqualInValue && !allowHttpSepartorsV0) {
                            cookieCount = createCookie(name, cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                            state = 4;
                            start = i + 1;
                        }
                    } else if (c != ':' && !allowHttpSepartorsV0 && LegacyCookieSupport.isHttpSeparator(c)) {
                        // http separators are not allowed in V0 cookie value unless io.undertow.legacy.cookie.ALLOW_HTTP_SEPARATORS_IN_V0 is set to true.
                        // However, "<hostcontroller-name>:<server-name>" (e.g. master:node1) is added as jvmRoute (instance-id) by default in WildFly domain mode.
                        // Though ":" is http separator, we allow it by default. Because, when Undertow runs as a proxy server (mod_cluster),
                        // we need to handle jvmRoute containing ":" in the request cookie value correctly to maintain the sticky session.
                        cookieCount = createCookie(name, cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                        state = 4;
                        start = i + 1;
                    }
                    break;
                }
                case 3: {
                    //extract quoted value
                    if (c == '"') {
                        cookieCount = createCookie(name, containsEscapedQuotes ? unescapeDoubleQuotes(cookie.substring(start, i)) : cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                        state = 0;
                        start = i + 1;
                    }
                    // Skip the next double quote char '"' when it is escaped by backslash '\' (i.e. \") inside the quoted value
                    if (c == '\\' && (i + 1 < cookie.length()) && cookie.charAt(i + 1) == '"') {
                        // But..., do not skip at the following conditions
                        if (i + 2 == cookie.length()) { // Cookie: key="\" or Cookie: key="...\"
                            break;
                        }
                        if (i + 2 < cookie.length() && (cookie.charAt(i + 2) == ';'      // Cookie: key="\"; key2=...
                                || (commaIsSeperator && cookie.charAt(i + 2) == ','))) { // Cookie: key="\", key2=...
                            break;
                        }
                        // Skip the next double quote char ('"' behind '\') in the cookie value
                        i++;
                        containsEscapedQuotes = true;
                    }
                    break;
                }
                case 4: {
                    //skip value portion behind '='
                    if (c == ';' || (commaIsSeperator && c == ',')) {
                        state = 0;
                    }
                    start = i + 1;
                    break;
                }
            }
        }
        if (state == 2) {
            createCookie(name, cookie.substring(start), maxCookies, cookieCount, cookies, additional);
        }

        for (final Map.Entry<String, String> entry : cookies.entrySet()) {
            Cookie c = new CookieImpl(entry.getKey(), entry.getValue());
            String domain = additional.get(DOMAIN);
            if (domain != null) {
                c.setDomain(domain);
            }
            String version = additional.get(VERSION);
            if (version != null) {
                c.setVersion(Integer.parseInt(version));
            }
            String path = additional.get(PATH);
            if (path != null) {
                c.setPath(path);
            }
            parsedCookies.add(c);
        }

        // RFC 6265 treats the domain, path and version attributes of an RFC 2109 cookie as a separate cookies
        for (final Map.Entry<String, String> entry : additional.entrySet()) {
            if (DOMAIN.equals(entry.getKey())) {
                Cookie c = new CookieImpl(DOMAIN, entry.getValue());
                parsedCookies.add(c);
            } else if (PATH.equals(entry.getKey())) {
                Cookie c = new CookieImpl(PATH, entry.getValue());
                parsedCookies.add(c);
            } else if (VERSION.equals(entry.getKey())) {
                Cookie c = new CookieImpl(VERSION, entry.getValue());
                parsedCookies.add(c);
            }
        }
    }

    private static int createCookie(final String name, final String value, int maxCookies, int cookieCount,
            final Map<String, String> cookies, final Map<String, String> additional) {
        if (!name.isEmpty() && name.charAt(0) == '$') {
            if(additional.containsKey(name)) {
                return cookieCount;
            }
            additional.put(name, value);
            return cookieCount;
        } else {
            if (cookieCount == maxCookies) {
                throw UndertowMessages.MESSAGES.tooManyCookies(maxCookies);
            }
            if(cookies.containsKey(name)) {
                return cookieCount;
            }
            cookies.put(name, value);
            return ++cookieCount;
        }
    }

    private static String unescapeDoubleQuotes(final String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Replace all escaped double quote (\") to double quote (")
        char[] tmp = new char[value.length()];
        int dest = 0;
        for(int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\\' && (i + 1 < value.length()) && value.charAt(i + 1) == '"') {
                i++;
            }
            tmp[dest] = value.charAt(i);
            dest++;
        }
        return new String(tmp, 0, dest);
    }

    private Cookies() {

    }
}
