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
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.util.DateUtils;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class CookieImpl implements Cookie {

    private static final Integer DEFAULT_MAX_AGE = Integer.valueOf(-1);
    private static final boolean DEFAULT_HTTP_ONLY = false;
    private static final boolean DEFAULT_SECURE = false;
    private static final boolean DEFAULT_DISCARD = false;

    private final String name;
    private String value;
    private String path;
    private String domain;
    private Integer maxAge = DEFAULT_MAX_AGE;
    private Date expires;
    private boolean discard;
    private boolean secure = DEFAULT_SECURE;
    private boolean httpOnly = DEFAULT_HTTP_ONLY;
    private int version = 0;
    private String comment;
    private String sameSiteMode;
    private final Map<String, String> attributes;

    public CookieImpl(final String name, final String value) {
        this(name, value, null);
    }

    public CookieImpl(final String name) {
        this(name, null);
    }

    public CookieImpl(final String name, final String value, final Cookie cookiePrimer) {
        this.name = name;
        this.value = value;
        this.attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        //attribs will be synced one way or ther other, might as well just iterate over attrib
        if(cookiePrimer != null) {
            for (Entry<String, String> primers : cookiePrimer.getAttributes().entrySet()) {
                this.setAttribute(primers.getKey(), primers.getValue());
            }
        }
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
        setAttribute(COOKIE_PATH_ATTR, path, path == null);
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public CookieImpl setDomain(final String domain) {
        this.domain = domain;
        setAttribute(COOKIE_DOMAIN_ATTR, domain, domain == null);
        return this;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public CookieImpl setMaxAge(final Integer maxAge) {
        this.maxAge = maxAge;
        setAttribute(COOKIE_MAX_AGE_ATTR, String.valueOf(maxAge), maxAge == null);
        return this;
    }

    public boolean isDiscard() {
        return discard;
    }

    public CookieImpl setDiscard(final boolean discard) {
        this.discard = discard;
        setAttribute(COOKIE_DISCARD_ATTR, String.valueOf(discard), false);
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public CookieImpl setSecure(final boolean secure) {
        this.secure = secure;
        setAttribute(COOKIE_SECURE_ATTR, String.valueOf(secure), false);
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
        setAttribute(COOKIE_HTTP_ONLY_ATTR, String.valueOf(httpOnly), false);
        return this;
    }

    public Date getExpires() {
        return expires;
    }

    public CookieImpl setExpires(final Date expires) {
        this.expires = expires;
        if(expires != null) {
            setAttribute(COOKIE_EXPIRES_ATTR, DateUtils.toDateString(expires), false);
        } else {
            setAttribute(COOKIE_EXPIRES_ATTR, null, false);
        }
        return this;
    }

    public String getComment() {
        return comment;
    }

    public Cookie setComment(final String comment) {
        setAttribute(COOKIE_COMMENT_ATTR, comment, false);
        this.comment = comment;
        return this;
    }

    @Override
    public boolean isSameSite() {
        return this.sameSiteMode != null;
    }

    @Override
    public Cookie setSameSite(final boolean sameSite) {
        //NOP
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
            setAttribute(COOKIE_SAME_SITE_ATTR, mode, false);
        } else {
            UndertowLogger.REQUEST_LOGGER.warnf(UndertowMessages.MESSAGES.invalidSameSiteMode(mode, Arrays.toString(CookieSameSiteMode.values())), "Ignoring specified SameSite mode [%s] for cookie [%s]", mode, this.name);
        }
        return this;
    }

    @Override
    public String getAttribute(final String name) {
        return attributes.get(name);
    }

    @Override
    public Cookie setAttribute(final String name, final String value) {
        return setAttribute(name, value, true);
    }

    protected Cookie setAttribute(final String name, final String value, boolean performSync) {
        // less than ideal, but users may want to fiddle with it like that, we need to sync
        if (value != null) {
            if (performSync) {
                switch (name) {
                    case COOKIE_COMMENT_ATTR:
                        this.comment = value;
                        break;
                    case COOKIE_DOMAIN_ATTR:
                        this.domain = value;
                        break;
                    case COOKIE_HTTP_ONLY_ATTR:
                        this.httpOnly = Boolean.parseBoolean(value);
                        break;
                    case COOKIE_MAX_AGE_ATTR:
                        this.maxAge = Integer.parseInt(value);
                        break;
                    case COOKIE_PATH_ATTR:
                        this.path = value;
                        break;
                    case COOKIE_SAME_SITE_ATTR:
                        // enum will match constant name, no inner representation
                        this.sameSiteMode = CookieSameSiteMode.valueOf(value.toUpperCase()).toString();
                        break;
                    case COOKIE_SECURE_ATTR:
                        this.secure = Boolean.valueOf(value);
                        break;
                    case COOKIE_DISCARD_ATTR:
                        this.discard = Boolean.valueOf(value);
                        break;
                    case COOKIE_EXPIRES_ATTR:
                        this.expires = DateUtils.parseDate(value);
                        break;
                }
            }

            attributes.put(name, value);
        } else {
            switch (name) {
                case COOKIE_COMMENT_ATTR:
                    this.comment = null;
                    break;
                case COOKIE_DOMAIN_ATTR:
                    this.domain = null;
                    break;
                case COOKIE_HTTP_ONLY_ATTR:
                    this.httpOnly = DEFAULT_HTTP_ONLY;
                    break;
                case COOKIE_MAX_AGE_ATTR:
                    this.maxAge = DEFAULT_MAX_AGE;
                    break;
                case COOKIE_PATH_ATTR:
                    this.path = null;
                    break;
                case COOKIE_SAME_SITE_ATTR:
                    // enum will match constant name, no inner representation
                    this.sameSiteMode = null;
                    break;
                case COOKIE_SECURE_ATTR:
                    this.secure = DEFAULT_SECURE;
                    break;
                case COOKIE_DISCARD_ATTR:
                    this.discard = DEFAULT_DISCARD;
                    break;
                case COOKIE_EXPIRES_ATTR:
                    this.expires = null;
                    break;
            }

            attributes.remove(name);
        }
        return this;
    }

    @Override
    public Map<String, String> getAttributes() {
        return Map.copyOf(attributes);
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
