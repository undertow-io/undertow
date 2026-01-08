/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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

import java.util.Map;

import io.undertow.server.handlers.CookieImpl;

/**
 * Base class to handle cookie attribs
 */
public abstract class CookieAttributes <T extends CookieAttributes>{

    protected final CookieImpl kernel;

    protected CookieAttributes() {
        this.kernel = new CookieImpl("I_DONT_MATTER");
    }
//
//    protected CookieAttributes(final CookieImpl kernel) {
//        this.kernel = kernel;
//    }

    public String getPath() {
        return this.kernel.getPath();
    }

    public T setPath(final String path) {
        this.kernel.setPath(path);
        return (T)this;
    }

    public String getDomain() {
        return this.kernel.getDomain();
    }

    public T setDomain(final String domain) {
        this.kernel.setDomain(domain);
        return (T)this;
    }

    public boolean isDiscard() {
        return this.kernel.isDiscard();
    }

    public T setDiscard(final boolean discard) {
        this.kernel.setDiscard(discard);
        return (T)this;
    }

    public boolean isSecure() {
        return this.kernel.isSecure();
    }

    public T setSecure(final boolean secure) {
        this.kernel.setSecure(secure);
        return (T)this;
    }

    public boolean isHttpOnly() {
        return this.kernel.isHttpOnly();
    }

    public T setHttpOnly(final boolean httpOnly) {
        this.kernel.setHttpOnly(httpOnly);
        return (T)this;
    }

    public int getMaxAge() {
        return kernel.getMaxAge();
    }

    public T setMaxAge(final int maxAge) {
        this.kernel.setMaxAge(maxAge);
        return (T)this;
    }

    public String getComment() {
        return this.kernel.getComment();
    }

    public T setComment(final String comment) {
        this.kernel.setComment(comment);
        return (T)this;
    }

    public T setAttribute(final String name, final String value) {
        kernel.setAttribute(name, value);
        return (T)this;
    }

    public String getAttribute(final String name) {
        return kernel.getAttribute(name);
    }

    public Map<String, String> getAttributes() {
        return kernel.getAttributes();
    }
}
