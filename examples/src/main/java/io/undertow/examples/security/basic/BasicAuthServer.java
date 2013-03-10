package io.undertow.examples.security.basic;

import java.util.HashMap;
import java.util.Map;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.io.IoCallback;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Example of HTTP Basic auth
 *
 * @author Stuart Douglas
 */
@UndertowExample("Basic Authentication")
public class BasicAuthServer {

    public static void main(final String[] args) {

        System.out.println("You can login with the following credentials:");
        System.out.println("User: userOne Password: passwordOne");
        System.out.println("User: userTwo Password: passwordTwo");

        final Map<String, char[]> users = new HashMap<>(2);
        users.put("userOne", "passwordOne".toCharArray());
        users.put("userTwo", "passwordTwo".toCharArray());

        final IdentityManager identityManager = new MapIdentityManager(users);

        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .setDefaultHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        final SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
                        exchange.getResponseSender().send("Hello " + context.getAuthenticatedAccount().getPrincipal().getName(), IoCallback.END_EXCHANGE);
                    }
                })
                .setLoginConfig(
                        Undertow.loginConfig(identityManager)
                                .basicAuth("MyApp"))
                .build();
        server.start();
    }

}
