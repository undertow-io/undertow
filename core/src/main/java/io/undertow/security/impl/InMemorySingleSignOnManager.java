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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.undertow.security.idm.Account;
import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionManager;
import io.undertow.util.CopyOnWriteMap;
import org.jboss.logging.Logger;

/**
 * @author Stuart Douglas
 * @author Paul Ferraro
 */
public class InMemorySingleSignOnManager implements SingleSignOnManager {

    private static final Logger log = Logger.getLogger(InMemorySingleSignOnManager.class);

    private static final SecureRandomSessionIdGenerator SECURE_RANDOM_SESSION_ID_GENERATOR = new SecureRandomSessionIdGenerator();

    private final Map<String, SingleSignOn> ssoEntries = new ConcurrentHashMap<>();

    @Override
    public SingleSignOn findSingleSignOn(String ssoId) {
        return this.ssoEntries.get(ssoId);
    }

    @Override
    public SingleSignOn createSingleSignOn(Account account, String mechanism) {
        String id = SECURE_RANDOM_SESSION_ID_GENERATOR.createSessionId();
        SingleSignOn entry = new SimpleSingleSignOnEntry(id, account, mechanism);
        this.ssoEntries.put(id, entry);
        if(log.isTraceEnabled()) {
            log.tracef("Creating SSO ID %s for Principal %s and Roles %s.", id, account.getPrincipal().getName(), account.getRoles().toString());
        }
        return entry;
    }

    @Override
    public void removeSingleSignOn(SingleSignOn sso) {
        if(log.isTraceEnabled()) {
            log.tracef("Removing SSO ID %s.", sso.getId());
        }
        this.ssoEntries.remove(sso.getId());
    }

    private static class SimpleSingleSignOnEntry implements SingleSignOn {
        private final String id;
        private final Account account;
        private final String mechanismName;
        private final Map<SessionManager, Session> sessions = new CopyOnWriteMap<>();

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
