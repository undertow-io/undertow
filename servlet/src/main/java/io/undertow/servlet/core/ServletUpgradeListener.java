package io.undertow.servlet.core;

import java.nio.channels.Channel;

import javax.servlet.http.HttpUpgradeHandler;

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.spec.WebConnectionImpl;
import org.xnio.ChannelListener;
import org.xnio.StreamConnection;

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
        channel.getCloseSetter().set(new ChannelListener<Channel>() {
            @Override
            public void handleEvent(final Channel channel) {
                try {
                    instance.getInstance().destroy();
                } finally {
                    instance.release();
                }
            }
        });
        instance.getInstance().init(new WebConnectionImpl(channel));
    }
}
