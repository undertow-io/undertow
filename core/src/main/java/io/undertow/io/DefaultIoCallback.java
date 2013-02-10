package io.undertow.io;

import java.io.IOException;

import io.undertow.server.HttpServerExchange;
import org.xnio.IoUtils;

/**
 * A default callbakc implementation that simply ends the exchange
 *
 * @author Stuart Douglas
 */
public class DefaultIoCallback implements IoCallback {

    @Override
    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
        exchange.endExchange();
    }

    @Override
    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
        try {
            exchange.endExchange();
        } finally {
            IoUtils.safeClose(exchange.getConnection());
        }
    }
}
