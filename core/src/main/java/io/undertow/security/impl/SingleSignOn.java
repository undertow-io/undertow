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

package io.undertow.security.impl;

import io.undertow.security.idm.Account;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionManager;

/**
 * @author Stuart Douglas
 * @author Paul Ferraro
 */
public interface SingleSignOn extends Iterable<Session>, AutoCloseable {

    /**
     * Returns the unique identifier for this SSO.
     * @return this SSO's unique identifier
     */
    String getId();

    /**
     * Returns the account associated with this SSO.
     * @return an account
     */
    Account getAccount();

    /**
     * Returns the authentication mechanism used to create the account associated with this SSO.
     * @return an authentication mechanism
     */
    String getMechanismName();

    /**
     * Indicates whether or not the specified session is contained in the set of sessions to which the user is authenticated
     * @param session a session manager
     * @return
     */
    boolean contains(Session session);

    /**
     * Adds the specified session to the set of sessions to which the user is authenticated
     * @param session a session manager
     */
    void add(Session session);

    /**
     * Removes the specified session from the set of sessions to which the user is authenticated
     * @param session a session manager
     */
    void remove(Session session);

    /**
     * Returns the session associated with the deployment of the specified session manager
     * @param manager a session manager
     * @return a session
     */
    Session getSession(SessionManager manager);

    /**
     * Releases any resources acquired by this object.
     * Must be called after this object is no longer in use.
     */
    @Override
    void close();
}
