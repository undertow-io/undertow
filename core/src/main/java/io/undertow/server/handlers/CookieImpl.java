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

package io.undertow.server.handlers;

import java.util.Arrays;
import java.util.Date;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class CookieImpl implements Cookie {

    private final String name;
    private String value;
    private String path;
    private String domain;
    private Integer maxAge;
    private Date expires;
    private boolean discard;
    private boolean secure;
    private boolean httpOnly;
    private int version = 0;
    private String comment;
    private boolean sameSite;
    private String sameSiteMode;

    public CookieImpl(final String name, final String value) {
        this.name = name;
        this.value = value;
    }

    public CookieImpl(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public CookieImpl setValue(final String value) {
        this.value = value;
        return this;
    }

    public String getPath() {
        return path;
    }

    public CookieImpl setPath(final String path) {
        this.path = path;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public CookieImpl setDomain(final String domain) {
        this.domain = domain;
        return this;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public CookieImpl setMaxAge(final Integer maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public boolean isDiscard() {
        return discard;
    }

    public CookieImpl setDiscard(final boolean discard) {
        this.discard = discard;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public CookieImpl setSecure(final boolean secure) {
        this.secure = secure;
        return this;
    }

    public int getVersion() {
        return version;
    }

    public CookieImpl setVersion(final int version) {
        this.version = version;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public CookieImpl setHttpOnly(final boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public Date getExpires() {
        return expires;
    }

    public CookieImpl setExpires(final Date expires) {
        this.expires = expires;
        return this;
    }

    public String getComment() {
        return comment;
    }

    public Cookie setComment(final String comment) {
        this.comment = comment;
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
            UndertowLogger.REQUEST_LOGGER.tracef("Setting SameSite mode to [%s] for cookie [%s]", m, this.name);
            this.sameSiteMode = m;
            this.setSameSite(true);
        } else {
            UndertowLogger.REQUEST_LOGGER.warnf(UndertowMessages.MESSAGES.invalidSameSiteMode(mode, Arrays.toString(CookieSameSiteMode.values())), "Ignoring specified SameSite mode [%s] for cookie [%s]", mode, this.name);
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
        return "{CookieImpl@" + System.identityHashCode(this) + " name=" + getName() + " path=" + getPath() + " domain=" + getDomain() + "}";
    }

}
