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
package io.undertow.security.idm;

/**
 * The IdentityManager interface to be implemented by an identity manager implementation providing user verification and
 * identity loading to Undertow.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface IdentityManager {

    Account lookupAccount(final String id);

    Account verifyCredential(final Credential credential);

    boolean verifyCredential(final Account account, final Credential credential);

    /**
     * Return the password for an account. This is an optional method, as is only used
     * for digest auth where the original password is needed to compute the digest.
     *
     * This is an optional method. It is recommended that passwords be stored in a hashed
     * format, so for most identity managers it will not be possible nor desirable to
     * implement this method.
     *
     * @param account the account
     * @return The accounts password
     */
    char[] getPassword(final Account account);


    /**
     * Check if the given account is in the specified group.
     *
     * Note that this check is for identity manager level groups, such as LDAP groups. These groups
     * are then mapped to roles in the servlet module.
     *
     * @param account The account
     * @param group The group
     * @return <code>true</code> if the user is in the specified group
     */
    boolean isUserInGroup(final Account account, final String group);

}
