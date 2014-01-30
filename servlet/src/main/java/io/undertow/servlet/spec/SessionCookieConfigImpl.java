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

package io.undertow.servlet.spec;

import io.undertow.server.session.SessionCookieConfig;
import io.undertow.servlet.UndertowServletMessages;

/**
 * Adapts a {@link SessionCookieConfig} to a {@link javax.servlet.SessionCookieConfig}.
 * @author Paul Ferraro
 */
public class SessionCookieConfigImpl implements javax.servlet.SessionCookieConfig {

    private final SessionCookieConfig config;
    private final ServletContextImpl context;

    public SessionCookieConfigImpl(SessionCookieConfig config, ServletContextImpl context) {
        this.config = config;
        this.context = context;
    }

    @Override
    public void setName(String name) {
        if (this.context.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.config.setCookieName(name);
    }

    @Override
    public String getName() {
        return this.config.getCookieName();
    }

    @Override
    public void setDomain(String domain) {
        if (this.context.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.config.setDomain(domain);
    }

    @Override
    public String getDomain() {
        return this.config.getDomain();
    }

    @Override
    public void setPath(String path) {
        if (this.context.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.config.setPath(path);
    }

    @Override
    public String getPath() {
        return this.config.getPath();
    }

    @Override
    public void setComment(String comment) {
        if (this.context.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.config.setComment(comment);
    }

    @Override
    public String getComment() {
        return this.config.getComment();
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        if (this.context.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.config.setHttpOnly(httpOnly);
    }

    @Override
    public boolean isHttpOnly() {
        return this.config.isHttpOnly();
    }

    @Override
    public void setSecure(boolean secure) {
        if (this.context.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.config.setSecure(secure);
    }

    @Override
    public boolean isSecure() {
        return this.config.isSecure();
    }

    @Override
    public void setMaxAge(int maxAge) {
        if (this.context.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.config.setMaxAge(maxAge);
    }

    @Override
    public int getMaxAge() {
        return this.config.getMaxAge();
    }
}
