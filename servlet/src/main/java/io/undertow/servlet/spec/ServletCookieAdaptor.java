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

package io.undertow.servlet.spec;

import java.util.Date;

import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.UndertowServletMessages;

/**
 * Adaptor between and undertow and a servlet cookie
 *
 * @author Stuart Douglas
 */
public class ServletCookieAdaptor implements Cookie {

    private final javax.servlet.http.Cookie cookie;

    public ServletCookieAdaptor(final javax.servlet.http.Cookie cookie) {
        this.cookie = cookie;
    }

    @Override
    public String getName() {
        return cookie.getName();
    }

    @Override
    public String getValue() {
        return cookie.getValue();
    }

    @Override
    public Cookie setValue(final String value) {
        cookie.setValue(value);
        return this;
    }

    @Override
    public String getPath() {
        return cookie.getPath();
    }

    @Override
    public Cookie setPath(final String path) {
        cookie.setPath(path);
        return this;
    }

    @Override
    public String getDomain() {
        return cookie.getDomain();
    }

    @Override
    public Cookie setDomain(final String domain) {
        cookie.setDomain(domain);
        return this;
    }

    @Override
    public Integer getMaxAge() {
        return cookie.getMaxAge();
    }

    @Override
    public Cookie setMaxAge(final Integer maxAge) {
        cookie.setMaxAge(maxAge);
        return this;
    }

    @Override
    public boolean isDiscard() {
        return cookie.getMaxAge() < 0;
    }

    @Override
    public Cookie setDiscard(final boolean discard) {
        return this;
    }

    @Override
    public boolean isSecure() {
        return cookie.getSecure();
    }

    @Override
    public Cookie setSecure(final boolean secure) {
        cookie.setSecure(secure);
        return this;
    }

    @Override
    public int getVersion() {
        return cookie.getVersion();
    }

    @Override
    public Cookie setVersion(final int version) {
        cookie.setVersion(version);
        return this;
    }

    @Override
    public boolean isHttpOnly() {
        return cookie.isHttpOnly();
    }

    @Override
    public Cookie setHttpOnly(final boolean httpOnly) {
        cookie.setHttpOnly(httpOnly);
        return this;
    }

    @Override
    public Date getExpires() {
        return null;
    }

    @Override
    public Cookie setExpires(final Date expires) {
        throw UndertowServletMessages.MESSAGES.notImplemented();
    }

    @Override
    public String getComment() {
        return cookie.getComment();
    }

    @Override
    public Cookie setComment(final String comment) {
        cookie.setComment(comment);
        return this;
    }
}
