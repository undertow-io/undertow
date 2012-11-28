package io.undertow.server.handlers.security;

import java.util.Set;

import javax.security.auth.callback.Callback;

/**
 * TEMP HACK
 *
 * this allows
 *
 * @author Stuart Douglas
 */
public class RoleCallback implements Callback {

    private Set<String> roles;

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(final Set<String> roles) {
        this.roles = roles;
    }
}
