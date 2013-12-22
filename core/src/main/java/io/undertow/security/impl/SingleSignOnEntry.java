package io.undertow.security.impl;

import io.undertow.security.idm.Account;
import io.undertow.server.session.Session;
import io.undertow.util.CopyOnWriteMap;

import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class SingleSignOnEntry {

    private final Account account;
    private final String mechanismName;
    private final Map<String, Session> sessions = new CopyOnWriteMap<String, Session>();

    public SingleSignOnEntry(Account account, String mechanismName) {
        this.account = account;
        this.mechanismName = mechanismName;
    }

    public Account getAccount() {
        return account;
    }

    public Map<String, Session> getSessions() {
        return sessions;
    }

    public String getMechanismName() {
        return mechanismName;
    }
}
