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

import java.io.IOException;
import java.util.Deque;

import org.xnio.IoFuture;
import io.undertow.TexugoLogger;
import io.undertow.TexugoMessages;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.Headers;

/**
 * Handler that attaches the session to the request.
 * <p/>
 * This handler is also the place where session cookie configuration properties are configured.
 *
 * @author Stuart Douglas
 */
public class SessionAttachmentHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    private volatile SessionManager sessionManager;

    /**
     * The path of the session cookie.
     */
    private volatile String path;
    private volatile String domain;
    private volatile boolean discardOnExit = false;
    private volatile boolean secure = false;
    private volatile String cookieName = SessionCookieConfig.DEFAULT_SESSION_ID;

    public SessionAttachmentHandler(final SessionManager sessionManager) {
        if(sessionManager == null) {
            throw TexugoMessages.MESSAGES.sessionManagerMustNotBeNull();
        }
        this.sessionManager = sessionManager;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        if (sessionManager == null) {
            throw TexugoMessages.MESSAGES.sessionManagerMustNotBeNull();
        }
        exchange.putAttachment(SessionManager.ATTACHMENT_KEY, sessionManager);
        String path = this.path;
        if (path == null) {
            path = exchange.getResolvedPath();
        }

        exchange.putAttachment(SessionCookieConfig.ATTACHMENT_KEY, new SessionCookieConfig(cookieName, path, domain, discardOnExit, secure));
        final String sessionId = findSessionId(exchange);

        if (sessionId == null) {
            HttpHandlers.executeHandler(next, exchange, completionHandler);
        } else {
            final IoFuture<Session> session = sessionManager.getSession(exchange, sessionId);
            session.addNotifier(new IoFuture.Notifier<Session, Session>() {
                @Override
                public void notify(final IoFuture<? extends Session> ioFuture, final Session attachment) {
                    try {
                        if (ioFuture.getStatus() == IoFuture.Status.DONE) {
                            exchange.putAttachment(Session.ATTACHMENT_KEY, ioFuture.get());
                            HttpHandlers.executeHandler(next, exchange, completionHandler);
                        } else if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                            //we failed to get the session
                            TexugoLogger.REQUEST_LOGGER.getSessionFailed(ioFuture.getException());
                            HttpHandlers.executeHandler(ResponseCodeHandler.HANDLE_500, exchange, completionHandler);
                        } else {
                            TexugoLogger.REQUEST_LOGGER.unexpectedStatusGettingSession(ioFuture.getStatus());
                            HttpHandlers.executeHandler(ResponseCodeHandler.HANDLE_500, exchange, completionHandler);
                        }
                    } catch (IOException e) {
                        TexugoLogger.REQUEST_LOGGER.getSessionFailed(e);
                        HttpHandlers.executeHandler(ResponseCodeHandler.HANDLE_500, exchange, completionHandler);
                    }
                }
            }, null);
        }
    }

    //TODO: we need some real cookie handing utilities
    private String findSessionId(final HttpServerExchange exchange) {
        final Deque<String> cookies = exchange.getRequestHeaders().get(Headers.COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (!cookie.startsWith("$")) {
                    String[] parts = cookie.split("=");
                    if (parts.length == 2) {
                        if (parts[0].equals(cookieName)) {
                            String value = parts[1];
                            if (value.length() > 2) {
                                if (value.charAt(0) == '"') {
                                    int last = value.lastIndexOf("\"");
                                    String trimmed = value.substring(1, last);
                                    return trimmed;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }


    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        this.next = next;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public void setSessionManager(final SessionManager sessionManager) {
        if(sessionManager == null) {
            throw TexugoMessages.MESSAGES.sessionManagerMustNotBeNull();
        }
        this.sessionManager = sessionManager;
    }

    public String getPath() {
        return path;
    }

    public synchronized void setPath(final String path) {
        this.path = path;
    }

    public String getDomain() {
        return domain;
    }

    public synchronized void setDomain(final String domain) {
        this.domain = domain;
    }

    public boolean isDiscardOnExit() {
        return discardOnExit;
    }

    public synchronized void setDiscardOnExit(final boolean discardOnExit) {
        this.discardOnExit = discardOnExit;
    }

    public boolean isSecure() {
        return secure;
    }

    public synchronized void setSecure(final boolean secure) {
        this.secure = secure;
    }
}
