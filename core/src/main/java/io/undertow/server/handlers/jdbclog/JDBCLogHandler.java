package io.undertow.server.handlers.jdbclog;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class JDBCLogHandler implements HttpHandler {

    private final HttpHandler next;
    private final String formatString;
    private final DefaultJDBCLogReceiver jdbcLogReceiver;
    private final ExchangeCompletionListener exchangeCompletionListener = new JDBCLogCompletionListener();

    public JDBCLogHandler(final HttpHandler next, final DefaultJDBCLogReceiver jdbcLogReceiver, final String formatString, ClassLoader classLoader) {
        this.next = next;
        this.formatString = formatString;
        this.jdbcLogReceiver = jdbcLogReceiver;
    }


    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addExchangeCompleteListener(exchangeCompletionListener);
        next.handleRequest(exchange);
    }

    private class JDBCLogCompletionListener implements ExchangeCompletionListener {
        @Override
        public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
            try {
                jdbcLogReceiver.logMessage(formatString, exchange);
            } finally {
                nextListener.proceed();
            }
        }
    }

    @Override
    public String toString() {
        return "JDBCLogHandler{" +
                "formatString='" + formatString + '\'' +
                '}';
    }

}
