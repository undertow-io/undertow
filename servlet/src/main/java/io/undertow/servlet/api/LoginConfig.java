package io.undertow.servlet.api;

/**
 * @author Stuart Douglas
 */
public class LoginConfig {
    private final String authMethod;
    private final String realmName;
    private final String loginPage;
    private final String errorPage;


    public LoginConfig(String authMethod, String realmName, String loginPage, String errorPage) {
        this.authMethod = authMethod;
        this.realmName = realmName;
        this.loginPage = loginPage;
        this.errorPage = errorPage;
    }

    public LoginConfig(final String authMethod, final String realmName) {
        this(authMethod, realmName, null, null);
    }

    public String getAuthMethod() {
        return authMethod;
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
}
