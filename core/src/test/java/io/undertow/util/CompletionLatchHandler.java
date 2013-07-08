package io.undertow.util;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Stuart Douglas
 */
public class CompletionLatchHandler implements HttpHandler {

    private final HttpHandler next;
    private volatile CountDownLatch latch = new CountDownLatch(1);

    public CompletionLatchHandler(HttpHandler next) {
        this.next = next;
    }

    public void await() {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void reset() {
        this.latch = new CountDownLatch(1);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                latch.countDown();
                nextListener.proceed();
            }
        });
        next.handleRequest(exchange);
    }
}
