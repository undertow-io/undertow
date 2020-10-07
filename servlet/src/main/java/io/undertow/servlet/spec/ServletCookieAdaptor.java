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

import java.util.Arrays;
import java.util.Date;

import io.undertow.UndertowMessages;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieSameSiteMode;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;

/**
 * Adaptor between and undertow and a servlet cookie
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ServletCookieAdaptor implements Cookie {

    private final javax.servlet.http.Cookie cookie;

    private boolean sameSite;
    private String sameSiteMode;

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

    @Override
    public boolean isSameSite() {
        return sameSite;
    }

    @Override
    public Cookie setSameSite(final boolean sameSite) {
        this.sameSite = sameSite;
        return this;
    }

    @Override
    public String getSameSiteMode() {
        return sameSiteMode;
    }

    @Override
    public Cookie setSameSiteMode(final String mode) {
        final String m = CookieSameSiteMode.lookupModeString(mode);
        if (m != null) {
            UndertowServletLogger.REQUEST_LOGGER.tracef("Setting SameSite mode to [%s] for cookie [%s]", m, this.getName());
            this.sameSiteMode = m;
            this.setSameSite(true);
        } else {
            UndertowServletLogger.REQUEST_LOGGER.warnf(UndertowMessages.MESSAGES.invalidSameSiteMode(mode, Arrays.toString(CookieSameSiteMode.values())), "Ignoring specified SameSite mode [%s] for cookie [%s]", mode, this.getName());
        }
        return this;
    }

    @Override
    public final int hashCode() {
        int result = 17;
        result = 37 * result + (getName() == null ? 0 : getName().hashCode());
        result = 37 * result + (getPath() == null ? 0 : getPath().hashCode());
        result = 37 * result + (getDomain() == null ? 0 : getDomain().hashCode());
        return result;
    }

    @Override
    public final boolean equals(final Object other) {
        if (other == this) return true;
        if (!(other instanceof Cookie)) return false;
        final Cookie o = (Cookie) other;
        // compare names
        if (getName() == null && o.getName() != null) return false;
        if (getName() != null && !getName().equals(o.getName())) return false;
        // compare paths
        if (getPath() == null && o.getPath() != null) return false;
        if (getPath() != null && !getPath().equals(o.getPath())) return false;
        // compare domains
        if (getDomain() == null && o.getDomain() != null) return false;
        if (getDomain() != null && !getDomain().equals(o.getDomain())) return false;
        // same cookie
        return true;
    }

    @Override
    public final int compareTo(final Object other) {
        return Cookie.super.compareTo(other);
    }

    @Override
    public final String toString() {
        return "{ServletCookieAdaptor@" + System.identityHashCode(this) + " name=" + getName() + " path=" + getPath() + " domain=" + getDomain() + "}";
    }

}
