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

package io.undertow.servlet.handlers.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.undertow.security.api.AuthenticationState;
import io.undertow.security.impl.SecurityContext;

/**
 * @author Stuart Douglas
 */
public class ServletRoleMappings {

    private final SecurityContext securityContext;
    private final Map<String, Set<String>> principleVsRoleMappings;
    private final Map<String, Set<String>> roleVsPrincipleMappings;
    private final Map<String, Boolean> cache = new HashMap<String, Boolean>();

    public ServletRoleMappings(final SecurityContext securityContext, final Map<String, Set<String>> principleVsRoleMappings, final Map<String, Set<String>> roleVsPrincipleMappings) {
        this.securityContext = securityContext;
        this.principleVsRoleMappings = principleVsRoleMappings;
        this.roleVsPrincipleMappings = roleVsPrincipleMappings;
    }

    public boolean isUserInRole(final String role) {
        if(securityContext.getAuthenticationState() != AuthenticationState.AUTHENTICATED) {
            return false;
        }

        Boolean result = cache.get(role);
        if (result == null) {
            result = false;
            String principle = securityContext.getAuthenticatedPrincipal().getName();
            if (principle.equals(role)) {
                result = true;
            } else {
                Set<String> principleGroups = principleVsRoleMappings.get(principle);
                if (principleGroups != null && principleGroups.contains(role)) {
                    result = true;
                } else {
                    Set<String> groupRoles = roleVsPrincipleMappings.get(role);
                    if (groupRoles != null) {
                        for (String group : groupRoles) {
                            if(securityContext.isUserInGroup(group)) {
                                result = true;
                                break;
                            }
                        }
                    } else {
                        //the role was not mapped, we check it directly
                        result = securityContext.isUserInGroup(role);
                    }
                }
            }
            cache.put(role, result);
        }
        return result;
    }
}
