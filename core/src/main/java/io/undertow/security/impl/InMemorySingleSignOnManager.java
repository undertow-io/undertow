package io.undertow.security.impl;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.undertow.security.idm.Account;
import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionManager;
import io.undertow.util.CopyOnWriteMap;

/**
 * @author Stuart Douglas
 * @author Paul Ferraro
 */
public class InMemorySingleSignOnManager implements SingleSignOnManager {

    private static final SecureRandomSessionIdGenerator SECURE_RANDOM_SESSION_ID_GENERATOR = new SecureRandomSessionIdGenerator();

    private final Map<String, SingleSignOn> ssoEntries = new ConcurrentHashMap<String, SingleSignOn>();

    @Override
    public SingleSignOn findSingleSignOn(String ssoId) {
        return this.ssoEntries.get(ssoId);
    }

    @Override
    public SingleSignOn createSingleSignOn(Account account, String mechanism) {
        String id = SECURE_RANDOM_SESSION_ID_GENERATOR.createSessionId();
        SingleSignOn entry = new SimpleSingleSignOnEntry(id, account, mechanism);
        this.ssoEntries.put(id, entry);
        return entry;
    }

    @Override
    public void removeSingleSignOn(String ssoId) {
        this.ssoEntries.remove(ssoId);
    }

    private static class SimpleSingleSignOnEntry implements SingleSignOn {
        private final String id;
        private final Account account;
        private final String mechanismName;
        private final Map<SessionManager, Session> sessions = new CopyOnWriteMap<SessionManager, Session>();

        SimpleSingleSignOnEntry(String id, Account account, String mechanismName) {
            this.id = id;
            this.account = account;
            this.mechanismName = mechanismName;
        }

        @Override
        public String getId() {
            return this.id;
        }

        @Override
        public Account getAccount() {
            return this.account;
        }

        @Override
        public String getMechanismName() {
            return this.mechanismName;
        }

        @Override
        public Iterator<Session> iterator() {
            return Collections.unmodifiableCollection(this.sessions.values()).iterator();
        }

        @Override
        public boolean contains(Session session) {
            return this.sessions.containsKey(session.getSessionManager());
        }

        @Override
        public Session getSession(SessionManager manager) {
            return this.sessions.get(manager);
        }

        @Override
        public void add(Session session) {
            this.sessions.put(session.getSessionManager(), session);
        }

        @Override
        public void remove(Session session) {
            this.sessions.remove(session.getSessionManager());
        }

        @Override
        public void close() {
            // Do nothing
        }
    }
}
