package io.undertow.server.handlers.accesslog;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Access log handler. This handler will generate access log messages based on the provided format string,
 * and pass these messages into the provided {@link AccessLogReceiver}.
 *
 * @author Stuart Douglas
 */
public class AccessLogHandler implements HttpHandler {

    private final HttpHandler next;
    private final AccessLogReceiver accessLogReceiver;
    private final String formatString;
    private final TokenHandler[] tokens;
    private final ExchangeCompletionListener exchangeCompletionListener = new AccessLogCompletionListener();

    public AccessLogHandler(final HttpHandler next, final AccessLogReceiver accessLogReceiver, final String formatString, TokenHandler.Factory... factories) {
        this.next = next;
        this.accessLogReceiver = accessLogReceiver;
        this.formatString = formatString;
        final List<TokenHandler> tokenHandlers = new ArrayList<TokenHandler>();
        StringTokenizer tokeniser = new StringTokenizer(formatString, " ", false);
        while (tokeniser.hasMoreElements()) {
            String elem = (String) tokeniser.nextElement();
            TokenHandler tokenHandler = null;
            for(TokenHandler.Factory factory : factories) {
                tokenHandler = factory.create(elem);
                if(tokenHandler != null) {
                    break;
                }
            }
            if(tokenHandler == null) {
                tokenHandler = new ConstantAccessLogToken(elem);
            }
            tokenHandlers.add(tokenHandler);
        }

        this.tokens = tokenHandlers.toArray(new TokenHandler[tokenHandlers.size()]);
    }


    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.addExchangeCompleteListener(exchangeCompletionListener);
        next.handleRequest(exchange);
    }

    private class AccessLogCompletionListener implements ExchangeCompletionListener {
        @Override
        public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
            StringBuilder builder = new StringBuilder();
            for(int i = 0; i < tokens.length; ++i) {
                String result = tokens[i].generateMessage(exchange);
                if(result == null) {
                    builder.append('-');
                } else {
                    builder.append(result);
                }
                if(i != tokens.length -1) {
                    builder.append(' ');
                }
            }
            accessLogReceiver.logMessage(builder.toString());
        }
    }

    @Override
    public String toString() {
        return "AccessLogHandler{" +
                "formatString='" + formatString + '\'' +
                '}';
    }

}
