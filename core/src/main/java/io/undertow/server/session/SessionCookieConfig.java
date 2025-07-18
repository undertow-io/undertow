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

package io.undertow.server.session;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.CookieSameSiteMode;

/**
 * Encapsulation of session cookie configuration. This removes the need for the session manager to
 * know about cookie configuration.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class SessionCookieConfig implements SessionConfig {

    public static final String DEFAULT_SESSION_ID = "JSESSIONID";

    private static final String COOKIE_COMMENT_ATTR = "Comment";
    private static final String COOKIE_DOMAIN_ATTR = "Domain";
    private static final String COOKIE_MAX_AGE_ATTR = "Max-Age";
    private static final String COOKIE_PATH_ATTR = "Path";
    private static final String COOKIE_SECURE_ATTR = "Secure";
    private static final String COOKIE_HTTP_ONLY_ATTR = "HttpOnly";
    private static final String COOKIE_SAME_SITE_ATTR = "SameSite";
    private static final String COOKIE_DISCARD_ATTR = "Discard";

    private static final Set<String> STANDARD_ATTR_NAMES;
    static {
        Set<String> tmp = new HashSet<String>(8);
        tmp.add(COOKIE_COMMENT_ATTR);
        tmp.add(COOKIE_DOMAIN_ATTR);
        tmp.add(COOKIE_MAX_AGE_ATTR);
        tmp.add(COOKIE_PATH_ATTR);
        tmp.add(COOKIE_SECURE_ATTR);
        tmp.add(COOKIE_HTTP_ONLY_ATTR);
        tmp.add(COOKIE_SAME_SITE_ATTR);
        tmp.add(COOKIE_DISCARD_ATTR);
        STANDARD_ATTR_NAMES = Collections.unmodifiableSet(tmp);
    }
    private static final int DEFAULT_MAX_AGE = -1;
    private static final boolean DEFAULT_HTTP_ONLY = false;
    private static final boolean DEFAULT_SECURE = false;

    private String cookieName = DEFAULT_SESSION_ID;
    private boolean discard;
    private String path = "/";
    private String domain;
    private boolean secure = DEFAULT_SECURE;
    private boolean httpOnly = DEFAULT_HTTP_ONLY;
    private int maxAge = DEFAULT_MAX_AGE;
    private String comment;
    private CookieSameSiteMode sameSite;
    private final Map<String,String> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    @Override
    public String rewriteUrl(final String originalUrl, final String sessionId) {
        return originalUrl;
    }

    @Override
    public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
        CookieImpl cookie = new CookieImpl(cookieName, sessionId)
                .setPath(path)
                .setDomain(domain)
                .setDiscard(discard)
                .setSecure(secure)
                .setHttpOnly(httpOnly)
                .setComment(comment);
        if (maxAge > 0) {
            cookie.setMaxAge(maxAge);
        }
        if(this.sameSite != null) {
            cookie.setSameSiteMode(String.valueOf(this.sameSite));
        }
        cookie.setAttributes(getNonStandardAttributes());
        exchange.setResponseCookie(cookie);
        UndertowLogger.SESSION_LOGGER.tracef("Setting session cookie session id %s on %s", sessionId, exchange);
    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final String sessionId) {
        CookieImpl cookie = new CookieImpl(cookieName, sessionId)
                .setPath(path)
                .setDomain(domain)
                .setDiscard(discard)
                .setSecure(secure)
                .setHttpOnly(httpOnly)
                .setMaxAge(0);
        if(this.sameSite != null) {
            cookie.setSameSiteMode(String.valueOf(this.sameSite));
        }
        cookie.setAttributes(getNonStandardAttributes());
        exchange.setResponseCookie(cookie);
        UndertowLogger.SESSION_LOGGER.tracef("Clearing session cookie session id %s on %s", sessionId, exchange);
    }

    @Override
    public String findSessionId(final HttpServerExchange exchange) {
        final Cookie cookie = exchange.getRequestCookie(cookieName);
        if (cookie != null) {
            UndertowLogger.SESSION_LOGGER.tracef("Found session cookie session id %s on %s", cookie, exchange);
            return cookie.getValue();
        }
        return null;
    }

    @Override
    public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
        return findSessionId(exchange) != null ? SessionCookieSource.COOKIE : SessionCookieSource.NONE;
    }

    public String getCookieName() {
        return cookieName;
    }

    public SessionCookieConfig setCookieName(final String cookieName) {
        this.cookieName = cookieName;
        return this;
    }

    public String getPath() {
        return path;
    }

    public SessionCookieConfig setPath(final String path) {
        this.path = path;
        setAttribute(COOKIE_PATH_ATTR, path);
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public SessionCookieConfig setDomain(final String domain) {
        this.domain = domain;
        setAttribute(COOKIE_DOMAIN_ATTR, domain);
        return this;
    }

    public boolean isDiscard() {
        return discard;
    }

    public SessionCookieConfig setDiscard(final boolean discard) {
        this.discard = discard;
        //atr ?
        return this;
    }

    public boolean isSecure() {
        return this.secure;
    }

    public SessionCookieConfig setSecure(final boolean secure) {
        this.secure = secure;
        setAttribute(COOKIE_SECURE_ATTR, String.valueOf(secure));
        return this;
    }

    public boolean isHttpOnly() {
        return this.httpOnly;
    }

    public SessionCookieConfig setHttpOnly(final boolean httpOnly) {
        this.httpOnly = httpOnly;
        setAttribute(COOKIE_HTTP_ONLY_ATTR, String.valueOf(httpOnly));
        return this;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public SessionCookieConfig setMaxAge(final int maxAge) {
        this.maxAge = maxAge;
        setAttribute(COOKIE_MAX_AGE_ATTR, String.valueOf(maxAge));
        return this;
    }

    public String getComment() {
        return comment;
    }

    public SessionCookieConfig setComment(final String comment) {
        this.comment = comment;
        setAttribute(COOKIE_COMMENT_ATTR, comment);
        return this;
    }

    public CookieSameSiteMode getSameSite() {
        return this.sameSite;
    }

    public SessionCookieConfig setSameSite(final CookieSameSiteMode sameSite) {
        this.sameSite = sameSite;
        setAttribute(COOKIE_SAME_SITE_ATTR, String.valueOf(sameSite), false);
        return this;
    }

    public boolean isSameSite() {
        //should be fale for NONE as well?
        return this.sameSite != null;
    }
    public SessionCookieConfig setAttribute(final String name, final String value) {
        return setAttribute(name, value, true);
    }

    protected SessionCookieConfig setAttribute(final String name, final String value, boolean performSync) {
        //less than ideal, but users may want to fiddle with it like that, we need to sync
        if(performSync) {
            switch(name) {
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
                    //enum will match constant name, no inner representation
                    this.sameSite = CookieSameSiteMode.valueOf(value.toUpperCase());
                    break;
                case COOKIE_SECURE_ATTR:
                    this.secure = Boolean.valueOf(value);
                    break;
                case COOKIE_DISCARD_ATTR:
                    this.discard = Boolean.valueOf(value);
                    break;
            }
        }
        attributes.put(name, value);
        return this;
    }

    public String getAttribute(final String name) {
        return attributes.get(name);
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Get non standard attribute map.
     * @return
     */
    protected Map<String, String> getNonStandardAttributes(){
        //standard attribs are handled directly. We need to remove those from attribs and present
        //during cookie dough kneeding so all of them end up sent over wire
        return Collections.unmodifiableMap(attributes.entrySet().stream().filter(entry -> {
            return !STANDARD_ATTR_NAMES.contains(entry.getKey());
        }).collect(Collectors.toMap(Entry<String, String>::getKey, Entry<String, String>::getValue)));
    }
}
