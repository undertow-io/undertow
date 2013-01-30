/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
package io.undertow.security.impl;

import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.security.api.RoleMappingManager;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;

/**
 * @author Stuart Douglas
 */
public class RoleMappingManagerImpl implements RoleMappingManager {


    private final Map<String, Set<String>> principleVsRoleMappings;
    private final Map<String, Set<String>> roleVsPrincipleMappings;

    public RoleMappingManagerImpl(final Map<String, Set<String>> principleVsRoleMappings) {
        this.principleVsRoleMappings = principleVsRoleMappings;
        final Map<String, Set<String>> roleVsPrincipleMappings = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : principleVsRoleMappings.entrySet()) {
            for (String val : entry.getValue()) {
                Set<String> principles = roleVsPrincipleMappings.get(val);
                if (principles == null) {
                    roleVsPrincipleMappings.put(val, principles = new HashSet<String>());
                }
                principles.add(entry.getKey());
            }
        }
        this.roleVsPrincipleMappings = roleVsPrincipleMappings;
    }

    @Override
    public boolean isUserInRole(final String role, final SecurityContext securityContext) {
        if (securityContext.isAuthenticated() == false) {
            return false;
        }
        Account account = securityContext.getAuthenticatedAccount();
        Principal principal = account.getPrincipal();
        if (principal.getName().equals(role)) {
            return true;
        } else {
            Set<String> principleGroups = principleVsRoleMappings.get(principal.getName());
            if (principleGroups != null && principleGroups.contains(role)) {
                return true;
            } else {
                Set<String> groupRoles = roleVsPrincipleMappings.get(role);
                if (groupRoles != null) {
                    for (String group : groupRoles) {
                        if (account.isUserInGroup(group)) {
                            return true;
                        }
                    }
                } else {
                    return account.isUserInGroup(role);
                }
            }
        }
        return false;
    }
}
