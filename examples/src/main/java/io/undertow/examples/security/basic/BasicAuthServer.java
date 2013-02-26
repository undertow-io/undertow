package io.undertow.examples.security.basic;

import java.util.HashMap;
import java.util.Map;

import io.undertow.Undertow;
import io.undertow.io.IoCallback;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * Example of HTTP Basic auth
 *
 *
 * @author Stuart Douglas
 */
public class BasicAuthServer {

    public static void main(final String[] args) {


        final Map<String, char[]> users = new HashMap<>(2);
        users.put("userOne", "passwordOne".toCharArray());
        users.put("userTwo", "passwordTwo".toCharArray());

        final IdentityManager identityManager = new MapIdentityManager(users);

        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .setDefaultHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) {
                        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "11");
                        exchange.getResponseSender().send("Hello World", IoCallback.END_EXCHANGE);
                    }
                })
                .setLoginConfig(
                        Undertow.loginConfig(identityManager)
                                .basicAuth("MyApp"))
                .build();
        server.start();
    }

}
