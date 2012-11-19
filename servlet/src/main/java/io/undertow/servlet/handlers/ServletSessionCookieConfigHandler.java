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

package io.undertow.servlet.handlers;

import javax.servlet.ServletContext;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.session.SessionCookieConfig;

/**
 * @author Stuart Douglas
 */
public class ServletSessionCookieConfigHandler implements HttpHandler {

    private volatile HttpHandler next;
    private final ServletContext servletContext;

    public ServletSessionCookieConfigHandler(final HttpHandler next, final ServletContext servletContext) {
        this.next = next;
        this.servletContext = servletContext;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        javax.servlet.SessionCookieConfig c = servletContext.getSessionCookieConfig();
        exchange.putAttachment(SessionCookieConfig.ATTACHMENT_KEY, new SessionCookieConfig(c.getName(), c.getPath(), c.getDomain(), false, c.isSecure(), c.isHttpOnly(), c.getMaxAge(), c.getComment()));
        HttpHandlers.executeHandler(next, exchange, completionHandler);
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }
}
