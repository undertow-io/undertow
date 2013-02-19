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

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Interface that manages sessions.
 *
 * The session manager is responsible for maintaining session state.
 *
 * As part of session creation the session manager MUST attempt to retrieve the {@link SessionCookieConfig} from
 * the {@link HttpServerExchange} and use it to set the session cookie. The frees up the session manager from
 * needing to know details of the cookie configuration. When invalidating a session the session manager MUST
 * also use this to clear the session cookie.
 *
 *
 * @author Stuart Douglas
 */
public interface SessionManager {

    AttachmentKey<SessionManager> ATTACHMENT_KEY = AttachmentKey.create(SessionManager.class);

    /**
     * Creates a new session. Any {@link SessionListener}s registered with this manager will be notified
     * of the session creation.
     *
     * This method *MUST* call {@link SessionConfig#findSession(io.undertow.server.HttpServerExchange)} first to
     * determine if an existing session ID is present in the exchange. If this id is present then it must be used
     * as the new session ID. If a session with this ID already exists then an {@link IllegalStateException} must be
     * thrown.
     *
     * this method *MUST* call {@link SessionConfig#attachSession(io.undertow.server.HttpServerExchange, Session)}
     * on the newly created session to attach it to the exchange.
     *
     *
     * This requirement exists to allow forwards across servlet contexts to work correctly.
     *
     *
     * @return The created session
     */
    Session createSession(final HttpServerExchange serverExchange, final SessionConfig sessionCookieConfig);

    /**
     *
     * @param sessionId The session id
     * @return An IoFuture that can be used to retrieve the session, or an IoFuture that will return null if not found
     */
    Session getSession(final HttpServerExchange serverExchange, final SessionConfig sessionCookieConfig);

    /**
     * Registers a session listener for the session manager
     *
     * @param listener The listener
     */
    void registerSessionListener(final SessionListener listener);

    /**
     * Removes a session listener from the session manager
     * @param listener the listener
     */
    void removeSessionListener(final SessionListener listener);

    /**
     * Sets the defaul session timeout
     * @param timeout the timeout
     */
    void setDefaultSessionTimeout(final int timeout);


}
