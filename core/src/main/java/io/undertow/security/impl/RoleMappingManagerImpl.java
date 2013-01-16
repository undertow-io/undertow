package io.undertow.security.impl;

import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.security.api.AuthenticationState;
import io.undertow.security.api.RoleMappingManager;

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
        if (securityContext.getAuthenticationState() != AuthenticationState.AUTHENTICATED) {
            return false;
        }
        Principal principal = securityContext.getAuthenticatedPrincipal();
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
                        if (securityContext.isUserInGroup(group)) {
                            return true;
                        }
                    }
                } else {
                    //the role was not mapped, we check it directly
                    return securityContext.isUserInGroup(role);
                }
            }
        }
        return false;
    }
}
