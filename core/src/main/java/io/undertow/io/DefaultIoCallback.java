package io.undertow.io;

import java.io.IOException;

import io.undertow.server.HttpServerExchange;
import org.xnio.IoUtils;

/**
 * A default callback implementation that simply ends the exchange
 *
 * @author Stuart Douglas
 */
public class DefaultIoCallback implements IoCallback {

    @Override
    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
        sender.close(new IoCallback() {
            @Override
            public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                exchange.endExchange();
            }

            @Override
            public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                exchange.endExchange();
            }
        });
    }

    @Override
    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
        try {
            exchange.setPersistent(false);
            exchange.endExchange();
        } finally {
            IoUtils.safeClose(exchange.getConnection());
        }
    }
}
