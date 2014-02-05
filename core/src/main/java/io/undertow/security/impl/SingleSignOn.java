package io.undertow.security.impl;

import java.io.Closeable;

import io.undertow.security.idm.Account;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionManager;

/**
 * @author Stuart Douglas
 * @author Paul Ferraro
 */
public interface SingleSignOn extends Iterable<Session>, Closeable {

    /**
     * Returns the unique identifier for this SSO.
     * @return
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
     * @param manager a session manager
     * @return
     */
    boolean contains(Session session);

    /**
     * Adds the specified session to the set of sessions to which the user is authenticated
     * @param manager a session manager
     */
    void add(Session session);

    /**
     * Removes the specified session from the set of sessions to which the user is authenticated
     * @param manager a session manager
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
