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

import java.util.Date;
import java.util.List;
import java.util.Map;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;

/**
 * A HTTP cookie.
 *
 * @see CookieHandler
 * @author Stuart Douglas
 */
public class Cookie {

    public static final AttachmentKey<Map<String, Cookie>> REQUEST_COOKIES = AttachmentKey.create(Map.class);
    public static final AttachmentKey<AttachmentList<Cookie>> RESPONSE_COOKIES = AttachmentKey.createList(Cookie.class);

    private final String name;
    private volatile String value;
    private volatile String path;
    private volatile String domain;
    private volatile Integer maxAge;
    private volatile Date expires;
    private volatile boolean discard;
    private volatile boolean secure;
    private volatile boolean httpOnly;
    private volatile int version = 0;


    public Cookie(final String name, final String value) {
        this.name = name;
        this.value = value;
    }

    public Cookie(final String name) {
        this.name = name;
    }

    public static Map<String, Cookie> getRequestCookies(final HttpServerExchange exchange) {
        return  exchange.getAttachment(REQUEST_COOKIES);
    }

    public static List<Cookie> getResponseCookies(final HttpServerExchange exchange) {
        return exchange.getAttachment(RESPONSE_COOKIES);
    }

    public static void addResponseCookie(final HttpServerExchange exchange, final Cookie cookie) {
        List<Cookie> cookies = exchange.getAttachment(RESPONSE_COOKIES);
        if(cookies == null) {
            throw UndertowMessages.MESSAGES.cookieHandlerNotPresent();
        }
        cookies.add(cookie);
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Cookie setValue(final String value) {
        this.value = value;
        return this;
    }

    public String getPath() {
        return path;
    }

    public Cookie setPath(final String path) {
        this.path = path;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public Cookie setDomain(final String domain) {
        this.domain = domain;
        return this;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public Cookie setMaxAge(final Integer maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public boolean isDiscard() {
        return discard;
    }

    public Cookie setDiscard(final boolean discard) {
        this.discard = discard;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public Cookie setSecure(final boolean secure) {
        this.secure = secure;
        return this;
    }

    public int getVersion() {
        return version;
    }

    public Cookie setVersion(final int version) {
        this.version = version;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public Cookie setHttpOnly(final boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public Date getExpires() {
        return expires;
    }

    public Cookie setExpires(final Date expires) {
        this.expires = expires;
        return this;
    }
}
