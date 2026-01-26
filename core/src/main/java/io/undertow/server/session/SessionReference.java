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
     * Returns the unique identifier of this object as assigned by the container.
     * @return the unique identifier of this object as assigned by the container.
     */
    String getId();
}
