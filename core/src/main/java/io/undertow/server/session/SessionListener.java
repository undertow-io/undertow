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

import io.undertow.server.HttpServerExchange;

/**
 *
 * A listener for session events.
 *
 *
 * @author Stuart Douglas
 */
public interface SessionListener {

    /**
     * Called when a session is created
     * @param session The new session
     * @param exchange The {@link HttpServerExchange} that created the session
     */
    default void sessionCreated(final Session session, final HttpServerExchange exchange) {
    }

    /**
     * Called when a session is destroyed
     * @param session   The new session
     * @param exchange  The {@link HttpServerExchange} that destroyed the session, or null if the session timed out
     * @param reason    The reason why the session was expired
     */
    default void sessionDestroyed(final Session session,  final HttpServerExchange exchange, SessionDestroyedReason reason) {
    }

    default void attributeAdded(final Session session, final String name, final Object value) {
    }

    default void attributeUpdated(final Session session, final String name, final Object newValue, final Object oldValue) {
    }

    default void attributeRemoved(final Session session, final String name,final Object oldValue) {
    }

    default void sessionIdChanged(final Session session, final String oldSessionId) {
    }

    enum SessionDestroyedReason {
        INVALIDATED,
        TIMEOUT,
        UNDEPLOY,
    }
}
