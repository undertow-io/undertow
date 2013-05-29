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

import java.security.Principal;

/**
 * Representation of an account, most likely a user account.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface Account {

    Principal getPrincipal();

    /**
     * Check if the given account has the specified role.
     * <p/>
     * Not that it is expected that the identity manager implementation returns an account which maps the users groups to roles
     * specific for the application.
     *
     * @param role The role.
     * @return <code>true</code> if the user has the specified role.
     */
    boolean isUserInRole(final String role);

    // TODO - Do we need a way to pass back to IDM that account is logging out? A few scenarios: -
    // 1 - Session expiration so cached account known to be logging out.
    // 2 - API call to logout.
    // 3 - End of HTTP request where account not cached, not strictly logging out but then again no real log-in.

}
