package io.undertow.util;

import io.undertow.UndertowMessages;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

/**
 * Class that contains utility methods for dealing with cookies.
 *
 * @author Stuart Douglas
 */
public class Cookies {


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
        if (key.toLowerCase().equals("path")) {
            cookie.setPath(value);
        } else if (key.toLowerCase().equals("domain")) {
            cookie.setDomain(value);
        } else if (key.toLowerCase().equals("max-age")) {
            cookie.setMaxAge(Integer.parseInt(value));
        } else if (key.toLowerCase().equals("expires")) {
            cookie.setExpires(DateUtils.parseDate(value));
        } else if (key.toLowerCase().equals("discard")) {
            cookie.setDiscard(true);
        } else if (key.toLowerCase().equals("secure")) {
            cookie.setSecure(true);
        } else if (key.toLowerCase().equals("httpOnly")) {
            cookie.setHttpOnly(true);
        } else if (key.toLowerCase().equals("version")) {
            cookie.setVersion(Integer.parseInt(value));
        } else if (key.toLowerCase().equals("comment")) {
            cookie.setComment(value);
        }
        //otherwise ignore this key-value pair
    }


    private Cookies() {

    }
}
