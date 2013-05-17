package io.undertow.examples.helloworld;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.io.IoCallback;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
@UndertowExample("Hello World")
public class HelloWorldServer {

    public static void main(final String[] args) {
        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .setDefaultHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("Hello World");
                    }
                }).build();
        server.start();
    }

}
