package io.undertow.security.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticator that can be used to configure single sign on.
 *
 * @author Stuart Douglas
 */
public class SingleSignOnAuthenticationMechanism extends AbstractSingleSignOnAuthenticationMechanism {

    private final Map<String, SingleSignOnEntry> ssoEntries = new ConcurrentHashMap<String, SingleSignOnEntry>();

    @Override
    protected SingleSignOnEntry findSsoEntry(String ssoId) {
        return ssoEntries.get(ssoId);
    }

    @Override
    protected void storeSsoEntry(String ssoId, SingleSignOnEntry entry) {
        ssoEntries.put(ssoId, entry);
    }

    @Override
    protected void removeSsoEntry(String sso) {
        ssoEntries.remove(sso);
    }
}
