/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.undertow.server.session;

/**
 * Encapsulates a reference to a session.
 * @author Paul Ferraro
 */
public interface SessionReference {
    /**
     * Returns the unique identifier of the referenced session.
     * @return the unique identifier of the referenced session.
     */
    String getId();

    /**
     * Returns the session manager of the referenced session.
     * @return the session manager of the referenced session.
     */
    SessionManager getSessionManager();
}
