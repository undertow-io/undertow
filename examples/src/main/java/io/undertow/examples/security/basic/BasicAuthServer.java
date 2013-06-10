package io.undertow.examples.security.basic;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.io.IoCallback;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Example of HTTP Basic auth
 * <p/>
 * TODO: this needs to be cleaned up
 *
 * @author Stuart Douglas
 */
@UndertowExample("Basic Authentication")
public class BasicAuthServer {

    public static void main(final String[] args) {

        System.out.println("You can login with the following credentials:");
        System.out.println("User: userOne Password: passwordOne");
        System.out.println("User: userTwo Password: passwordTwo");

        final Map<String, char[]> users = new HashMap<String, char[]>(2);
        users.put("userOne", "passwordOne".toCharArray());
        users.put("userTwo", "passwordTwo".toCharArray());

        final IdentityManager identityManager = new MapIdentityManager(users);

        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .setHandler(addSecurity(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        final SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
                        exchange.getResponseSender().send("Hello " + context.getAuthenticatedAccount().getPrincipal().getName(), IoCallback.END_EXCHANGE);
                    }
                }, identityManager))
                .build();
        server.start();
    }

    private static HttpHandler addSecurity(final HttpHandler toWrap, final IdentityManager identityManager) {
        HttpHandler handler = toWrap;
        handler = new AuthenticationCallHandler(handler);
        handler = new AuthenticationConstraintHandler(handler);
        final List<AuthenticationMechanism> mechanisms = Collections.<AuthenticationMechanism>singletonList(new BasicAuthenticationMechanism("My Realm"));
        handler = new AuthenticationMechanismsHandler(handler, mechanisms);
        handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);
        return handler;
    }
}
