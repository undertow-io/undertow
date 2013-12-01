package io.undertow.servlet.api;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class LoginConfig implements Cloneable {
    private final LinkedList<AuthMethodConfig> authMethods = new LinkedList<AuthMethodConfig>();
    private final String realmName;
    private final String loginPage;
    private final String errorPage;


    public LoginConfig(String realmName, String loginPage, String errorPage) {
        this.realmName = realmName;
        this.loginPage = loginPage;
        this.errorPage = errorPage;
    }

    public LoginConfig(final String realmName) {
        this(realmName, null, null);
    }

    public LoginConfig(String mechanismName, String realmName, String loginPage, String errorPage) {
        this.realmName = realmName;
        this.loginPage = loginPage;
        this.errorPage = errorPage;
        addFirstAuthMethod(mechanismName);
    }

    public LoginConfig(String mechanismName, final String realmName) {
        this(mechanismName, realmName, null, null);
    }

    public String getRealmName() {
        return realmName;
    }

    public String getLoginPage() {
        return loginPage;
    }

    public String getErrorPage() {
        return errorPage;
    }

    public LoginConfig addFirstAuthMethod(AuthMethodConfig authMethodConfig) {
        authMethods.add(authMethodConfig);
        return this;
    }

    public LoginConfig addLastAuthMethod(AuthMethodConfig authMethodConfig) {
        authMethods.addLast(authMethodConfig);
        return this;
    }
    public LoginConfig addFirstAuthMethod(String authMethodConfig) {
        authMethods.add(new AuthMethodConfig(authMethodConfig));
        return this;
    }

    public LoginConfig addLastAuthMethod(String authMethodConfig) {
        authMethods.addLast(new AuthMethodConfig(authMethodConfig));
        return this;
    }

    public List<AuthMethodConfig> getAuthMethods() {
        return authMethods;
    }

    @Override
    public LoginConfig clone() {
        LoginConfig lc = new LoginConfig(realmName, loginPage, errorPage);
        for(AuthMethodConfig method : authMethods) {
            lc.authMethods.add(method.clone());
        }
        return lc;
    }
}
