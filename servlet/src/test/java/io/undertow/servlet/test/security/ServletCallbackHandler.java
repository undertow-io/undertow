package io.undertow.servlet.test.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import io.undertow.server.handlers.security.RoleCallback;

/**
 * @author Stuart Douglas
 */
public class ServletCallbackHandler implements CallbackHandler {

    private final Map<String, User> users = new HashMap<String, User>();

    public void addUser(final String name, final String password, final String... roles) {
        User user = new User();
        user.password = password.toCharArray();
        user.roles = new HashSet<String>(Arrays.asList(roles));
        users.put(name, user);
    }

    @Override
    public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        NameCallback ncb = null;
        PasswordCallback pcb = null;
        RoleCallback rcb = null;
        for (Callback current : callbacks) {
            if (current instanceof NameCallback) {
                ncb = (NameCallback) current;
            } else if (current instanceof PasswordCallback) {
                pcb = (PasswordCallback) current;
            } else if (current instanceof RoleCallback) {
                rcb = (RoleCallback) current;
            } else {
                throw new UnsupportedCallbackException(current);
            }
        }

        User user = users.get(ncb.getDefaultName());
        if (user == null) {
            throw new IOException("User not found");
        }
        pcb.setPassword(user.password);
        rcb.setRoles(user.roles);
    }

    private static class User {
        char[] password;
        Set<String> roles;
    }

}
