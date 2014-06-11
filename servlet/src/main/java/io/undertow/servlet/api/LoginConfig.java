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

package io.undertow.servlet.api;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class LoginConfig implements Cloneable {
    private final LinkedList<AuthMethodConfig> authMethods = new LinkedList<>();
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
        authMethods.addFirst(authMethodConfig);
        return this;
    }

    public LoginConfig addLastAuthMethod(AuthMethodConfig authMethodConfig) {
        authMethods.addLast(authMethodConfig);
        return this;
    }
    public LoginConfig addFirstAuthMethod(String authMethodConfig) {
        authMethods.addFirst(new AuthMethodConfig(authMethodConfig));
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
