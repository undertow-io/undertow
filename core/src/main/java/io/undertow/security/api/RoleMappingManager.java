package io.undertow.security.api;

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
     * @param role The role to check
     * @param securityContext The current security context
     * @return <code>true</code> if the user is in the supplied role
     */
    boolean isUserInRole(final String role, final SecurityContext securityContext);

}
