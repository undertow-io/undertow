/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.servlet.test.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;

/**
 * A mock {@link IdentityManager} implementation for servlet security testing.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ServletIdentityManager implements IdentityManager {

    private final Map<String, User> users = new HashMap<String, User>();

    public void addUser(final String name, final String password, final String... roles) {
        User user = new User();
        user.name = name;
        user.password = password.toCharArray();
        user.roles = new HashSet<String>(Arrays.asList(roles));
        users.put(name, user);
    }

    @Override
    public Account lookupAccount(String id) {
        return users.get(id);
    }

    @Override
    public Account verifyCredential(Credential credential) {
        return null;
    }

    @Override
    public boolean verifyCredential(Account account, Credential credential) {
        // This approach should never be copied in a realm IdentityManager.
        if (account instanceof User && credential instanceof PasswordCredential) {
            char[] expectedPassword = ((User) account).password;
            char[] suppliedPassword = ((PasswordCredential) credential).getPassword();

            return Arrays.equals(expectedPassword, suppliedPassword);
        }
        return false;
    }

    @Override
    public char[] getPassword(final Account account) {
        return null;
    }

    @Override
    public boolean isUserInGroup(final Account account, final String group) {
        if (account instanceof User) {
            return ((User) account).roles.contains(group);
        }
        return false;

    }

    private static class User implements Account {
        // In no way whatsoever should a class like this be considered a good idea for a real IdentityManager implementation,
        // this is for testing only.

        String name;
        char[] password;
        Set<String> roles;

        @Override
        public String getName() {
            return name;
        }
    }

}
