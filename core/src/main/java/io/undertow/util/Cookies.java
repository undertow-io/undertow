package io.undertow.util;

import io.undertow.UndertowMessages;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Class that contains utility methods for dealing with cookies.
 *
 * @author Stuart Douglas
 */
public class Cookies {

    public static final String DOMAIN = "$Domain";
    public static final String VERSION = "$Version";
    public static final String PATH = "$Path";

    /**
     * Parses a Set-Cookie response header into its cookie representation.
     *
     * @param headerValue The header value
     * @return The cookie
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
                handleValue(cookie, key, headerValue.substring(current, headerValue.length()));
            } else {
                handleValue(cookie, key, null);
            }
        }

        return cookie;
    }

    private static void handleValue(CookieImpl cookie, String key, String value) {
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
        }
        //otherwise ignore this key-value pair
    }

    /**
     * Parses the cookies from a list of cookie headers
     * @param maxCookies The maximum number of cookies. Used to prevent hash collision attacks
     * @param cookies The cookie values to parse
     * @return A pared cookie map
     */
    public static Map<String, Cookie> parseRequestCookies(int maxCookies, List<String> cookies) {

        if (cookies == null) {
            return new TreeMap<String, Cookie>();
        }
        final Map<String, Cookie> parsedCookies = new TreeMap<String, Cookie>();

        for (String cookie : cookies) {
            parseCookie(cookie, parsedCookies, maxCookies);
        }
        return parsedCookies;
    }

    /**
     * @param cookie        The cookie
     * @param parsedCookies The map of cookies
     */
    private static void parseCookie(final String cookie, final Map<String, Cookie> parsedCookies, int maxCookies) {
        int state = 0;
        String name = null;
        int start = 0;
        int cookieCount = parsedCookies.size();
        final Map<String, String> cookies = new HashMap<String, String>();
        final Map<String, String> additional = new HashMap<String, String>();
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
                    if (c == '=') {
                        name = cookie.substring(start, i);
                        start = i + 1;
                        state = 2;
                    } else if (c == ';') {
                        final String value = cookie.substring(start, i);
                        if (++cookieCount == maxCookies) {
                            throw UndertowMessages.MESSAGES.tooManyCookies(maxCookies);
                        }
                        if (name.startsWith("$")) {
                            additional.put(name, value);
                        } else {
                            cookies.put(name, value);
                        }
                        state = 0;
                        start = i + 1;
                    }
                    break;
                }
                case 2: {
                    if (c == ';') {
                        final String value = cookie.substring(start, i);
                        if (++cookieCount == maxCookies) {
                            throw UndertowMessages.MESSAGES.tooManyCookies(maxCookies);
                        }
                        if (name.startsWith("$")) {
                            additional.put(name, value);
                        } else {
                            cookies.put(name, value);
                        }
                        state = 0;
                        start = i + 1;
                    } else if (c == '"') {
                        state = 3;
                        start = i + 1;
                    }
                    break;
                }
                case 3: {
                    if (c == '"') {
                        final String value = cookie.substring(start, i);
                        if (++cookieCount == maxCookies) {
                            throw UndertowMessages.MESSAGES.tooManyCookies(maxCookies);
                        }
                        if (name.startsWith("$")) {
                            additional.put(name, value);
                        } else {
                            cookies.put(name, value);
                        }
                        state = 0;
                        start = i + 1;
                    }
                    break;
                }
            }
        }
        if (state == 2) {
            final String value = cookie.substring(start);
            if (++cookieCount == maxCookies) {
                throw UndertowMessages.MESSAGES.tooManyCookies(maxCookies);
            }
            if (name.startsWith("$")) {
                additional.put(name, value);
            } else {
                cookies.put(name, value);
            }
        }

        for (final Map.Entry<String, String> entry : cookies.entrySet()) {
            Cookie c = new CookieImpl(entry.getKey(), entry.getValue());
            if (additional.containsKey(DOMAIN)) {
                c.setDomain(additional.get(DOMAIN));
            }
            if (additional.containsKey(VERSION)) {
                c.setVersion(Integer.parseInt(additional.get(VERSION)));
            }
            if (additional.containsKey(PATH)) {
                c.setPath(additional.get(PATH));
            }
            parsedCookies.put(c.getName(), c);
        }
    }

    private Cookies() {

    }
}
