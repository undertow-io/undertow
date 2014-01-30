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

package io.undertow.server.session;


/**
 * Encapsulation of session cookie configuration. This removes the need for the session manager to
 * know about cookie configuration.
 *
 * @author Stuart Douglas
 */
public class SessionCookieConfig {

    public static final String DEFAULT_SESSION_ID = "JSESSIONID";

    private String cookieName = DEFAULT_SESSION_ID;
    private String path = "/";
    private String domain;
    private boolean discard;
    private boolean secure;
    private boolean httpOnly;
    private int maxAge;
    private String comment;

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
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public SessionCookieConfig setDomain(final String domain) {
        this.domain = domain;
        return this;
    }

    public boolean isDiscard() {
        return discard;
    }

    public SessionCookieConfig setDiscard(final boolean discard) {
        this.discard = discard;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public SessionCookieConfig setSecure(final boolean secure) {
        this.secure = secure;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public SessionCookieConfig setHttpOnly(final boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public SessionCookieConfig setMaxAge(final int maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public String getComment() {
        return comment;
    }

    public SessionCookieConfig setComment(final String comment) {
        this.comment = comment;
        return this;
    }
}
