package io.undertow.servlet.core;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ThreadSetupAction;
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
    private final ThreadSetupAction threadSetupAction;

    public ServletUpgradeListener(final InstanceHandle<T> instance, ThreadSetupAction threadSetupAction) {
        this.instance = instance;
        this.threadSetupAction = threadSetupAction;
    }

    @Override
    public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
        final StreamConnection channel = exchange.getConnection().upgradeChannel();
        exchange.getConnection().addCloseListener(new ServerConnection.CloseListener() {
            @Override
            public void closed(ServerConnection connection) {
                final ThreadSetupAction.Handle handle = threadSetupAction.setup(exchange);
                try {
                    instance.getInstance().destroy();
                } finally {
                    try {
                        handle.tearDown();
                    } finally {
                        instance.release();
                    }
                }
            }
        });
        exchange.getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                final ThreadSetupAction.Handle handle = threadSetupAction.setup(exchange);
                try {
                    //run the upgrade in the IO thread, to prevent threading issues
                    instance.getInstance().init(new WebConnectionImpl(channel));
                } finally {
                    handle.tearDown();
                }
            }
        });
    }
}
