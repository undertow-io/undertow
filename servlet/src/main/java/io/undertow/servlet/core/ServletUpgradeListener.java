package io.undertow.servlet.core;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.spec.WebConnectionImpl;
import org.xnio.StreamConnection;

import javax.servlet.http.HttpUpgradeHandler;

/**
 * Lister that handles a servlet exchange upgrade event.
 *
 * @author Stuart Douglas
 */
public class ServletUpgradeListener<T extends HttpUpgradeHandler> implements ExchangeCompletionListener {
    private final InstanceHandle<T> instance;

    public ServletUpgradeListener(final InstanceHandle<T> instance) {
        this.instance = instance;
    }

    @Override
    public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
        final StreamConnection channel = exchange.getConnection().getChannel();
        exchange.getConnection().addCloseListener(new HttpServerConnection.CloseListener() {
            @Override
            public void closed(HttpServerConnection connection) {
                try {
                    instance.getInstance().destroy();
                } finally {
                    instance.release();
                }
            }
        });
        exchange.getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                //run the upgrade in the IO thread, to prevent threading issues
                instance.getInstance().init(new WebConnectionImpl(channel));
            }
        });
    }
}
