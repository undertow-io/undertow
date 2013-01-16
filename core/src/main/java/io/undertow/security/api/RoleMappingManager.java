package io.undertow.security.api;

import io.undertow.security.impl.SecurityContext;

/**
 *
 * Interface that is responsible for mapping a security context to a given application rules.
 *
 * Generally implementations will follow the rules specified by the servlet specification.
 *
 * @author Stuart Douglas
 */
public interface RoleMappingManager {

    /**
     * Checks if the current authenticated principal authenticated within the security context is mapped to
     * the given role.
     *
     * @param role
     * @param securityContext
     * @return
     */
    boolean isUserInRole(final String role, final SecurityContext securityContext);

}
