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

package io.undertow.server.handlers;

import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import io.undertow.server.ChannelWrapper;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentList;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author Stuart Douglas
 */
public class CookieHandler implements HttpHandler {

    public static final String DOMAIN = "$Domain";
    public static final String VERSION = "$Version";
    public static final String PATH = "$Path";
    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {

        final Map<String, Cookie> cookies = parseCookies(exchange);
        exchange.putAttachment(Cookie.REQUEST_COOKIES, new CopyOnWriteMap<String, Cookie>(cookies));
        exchange.putAttachment(Cookie.RESPONSE_COOKIES, new AttachmentList<Cookie>(Cookie.class));
        exchange.addResponseWrapper(CookieChannelWrapper.INSTANCE);
        HttpHandlers.executeHandler(next, exchange, completionHandler);
    }

    private static Map<String, Cookie> parseCookies(final HttpServerExchange exchange) {
        Deque<String> cookies = exchange.getRequestHeaders().get(Headers.COOKIE);

        if (cookies == null) {
            return Collections.emptyMap();
        }
        final Map<String, Cookie> parsedCookies = new HashMap<String, Cookie>();

        for (String cookie : cookies) {
            parseCookie(cookie, parsedCookies);
        }
        return parsedCookies;
    }

    /**
     * TODO: handle version 1 cookies
     *
     * @param cookie        The cookie
     * @param parsedCookies The map of cookies
     */
    private static void parseCookie(final String cookie, final Map<String, Cookie> parsedCookies) {
        int state = 0;
        String name = null;
        int start = 0;
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
                    }
                    break;
                }
                case 2: {
                    if (c == ';') {
                        final String value = cookie.substring(start, i);
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


    private static String getCookieString(final Cookie cookie) {
        switch (cookie.getVersion()) {
            case 0:
                return addVersion0ResponseCookieToExchange(cookie);
            case 1:
            default:
                return addVersion1ResponseCookieToExchange(cookie);
        }
    }

    private static String addVersion0ResponseCookieToExchange(final Cookie cookie) {
        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        header.append(cookie.getValue());

        if (cookie.getPath() != null) {
            header.append("; path=");
            header.append(cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            header.append("; domain=");
            header.append(cookie.getDomain());
        }
        if (cookie.isDiscard()) {
            header.append("; Discard");
        }
        if (cookie.isSecure()) {
            header.append("; Secure");
        }
        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }
        if (cookie.getExpires() != null) {
            header.append("; Expires=");
            header.append(DateUtils.toOldCookieDateString(cookie.getExpires()));
        } else if (cookie.getMaxAge() != null) {
            if (cookie.getMaxAge() == 0) {
                Date expires = new Date();
                expires.setTime(0);
                header.append("; Expires=");
                header.append(DateUtils.toOldCookieDateString(expires));
            } else if (cookie.getMaxAge() > 0) {
                Date expires = new Date();
                expires.setTime(expires.getTime() + cookie.getMaxAge());
                header.append("; Expires=");
                header.append(DateUtils.toOldCookieDateString(expires));
            }
        }
        return header.toString();

    }

    private static String addVersion1ResponseCookieToExchange(final Cookie cookie) {

        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        header.append(cookie.getValue());
        header.append("; Version=1");
        if (cookie.getPath() != null) {
            header.append("; Path=");
            header.append(cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            header.append("; Domain=");
            header.append(cookie.getDomain());
        }
        if (cookie.isDiscard()) {
            header.append("; Discard");
        }
        if (cookie.isSecure()) {
            header.append("; Secure");
        }
        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }
        if (cookie.getMaxAge() != null) {
            if (cookie.getMaxAge() >= 0) {
                header.append("; Max-Age=");
                header.append(cookie.getMaxAge());
            }
        }
        if (cookie.getExpires() != null) {
            header.append("; Expires=");
            header.append(DateUtils.toDateString(cookie.getExpires()));
        }
        return header.toString();
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

    private static class CookieChannelWrapper implements ChannelWrapper<StreamSinkChannel> {

        public static CookieChannelWrapper INSTANCE = new CookieChannelWrapper();

        @Override
        public StreamSinkChannel wrap(final StreamSinkChannel channel, final HttpServerExchange exchange) {

            final List<Cookie> cookies = exchange.getAttachmentList(Cookie.RESPONSE_COOKIES);
            if (!cookies.isEmpty()) {
                ListIterator<Cookie> it = cookies.listIterator();
                while (it.hasNext()) {
                    StringBuilder builder = new StringBuilder();
                    Cookie cookie = it.next();
                    builder.append(getCookieString(cookie));
                    exchange.getResponseHeaders().add(Headers.SET_COOKIE, builder.toString());
                }
            }
            return channel;
        }
    }
}
