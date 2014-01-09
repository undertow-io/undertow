package io.undertow.security.impl;

import io.undertow.security.idm.Account;

/**
 * @author Paul Ferraro
 */
public interface SingleSignOnManager {
    SingleSignOn createSingleSignOn(Account account, String mechanism);

    SingleSignOn findSingleSignOn(String ssoId);

    void removeSingleSignOn(String ssoId);
}
