package io.undertow.server.handlers.proxy;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;
import io.undertow.util.SameThreadExecutor;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channel;
import java.util.concurrent.TimeUnit;

/**
 * Simple proxy client provider. This provider simply proxies to another server, using a a one to one
 * connection strategy.
 *
 * @author Stuart Douglas
 */
public class SimpleProxyClientProvider implements ProxyClientProvider {

    private final URI uri;
    private final AttachmentKey<ProxyClient> clientAttachmentKey = AttachmentKey.create(ProxyClient.class);
    private final UndertowClient client;

    public SimpleProxyClientProvider(URI uri) {
        this.uri = uri;
        client = UndertowClient.getInstance();
    }

    @Override
    public void createProxyClient(final HttpServerExchange exchange, final HttpHandler nextHandler, final long timeout, final TimeUnit timeUnit) {
        ProxyClient existing = exchange.getConnection().getAttachment(clientAttachmentKey);
        if (existing != null) {
            if (existing.isOpen()) {
                //this connection already has a client, re-use it
                exchange.putAttachment(CLIENT, existing);
                exchange.dispatch(SameThreadExecutor.INSTANCE, nextHandler);
                return;
            } else {
                exchange.getConnection().removeAttachment(clientAttachmentKey);
            }
        }
        exchange.dispatch(SameThreadExecutor.INSTANCE, new Runnable() {
            @Override
            public void run() {
                client.connect(new ConnectNotifier(nextHandler, exchange), uri, exchange.getIoThread(), exchange.getConnection().getBufferPool(), OptionMap.EMPTY);
            }
        });
    }


    private final class ConnectNotifier implements ClientCallback<ClientConnection> {

        private final HttpHandler next;
        private final HttpServerExchange exchange;

        private ConnectNotifier(HttpHandler next, HttpServerExchange exchange) {
            this.next = next;
            this.exchange = exchange;
        }

        @Override
        public void completed(final ClientConnection connection) {
            final ServerConnection serverConnection = exchange.getConnection();
            final SimpleProxyClient simpleProxyClient = new SimpleProxyClient(connection);
            //we attach to the connection so it can be re-used
            serverConnection.putAttachment(clientAttachmentKey, simpleProxyClient);
            exchange.putAttachment(CLIENT, simpleProxyClient);
            serverConnection.addCloseListener(new ServerConnection.CloseListener() {
                @Override
                public void closed(ServerConnection serverConnection) {
                    IoUtils.safeClose(connection);
                }
            });
            connection.getCloseSetter().set(new ChannelListener<Channel>() {
                @Override
                public void handleEvent(Channel channel) {
                    serverConnection.removeAttachment(clientAttachmentKey);
                }
            });
            exchange.dispatch(SameThreadExecutor.INSTANCE, next);
        }

        @Override
        public void failed(IOException e) {
            exchange.putAttachment(THROWABLE, e);
            exchange.dispatch(SameThreadExecutor.INSTANCE, next);
        }
    }

    private static final class SimpleProxyClient implements ProxyClient {

        private final ClientConnection connection;

        private SimpleProxyClient(ClientConnection connection) {
            this.connection = connection;
        }

        @Override
        public void getConnection(HttpServerExchange exchange, HttpHandler nextHandler, long timeout, TimeUnit timeUnit) {
            exchange.putAttachment(CONNECTION, connection);
            exchange.dispatch(SameThreadExecutor.INSTANCE, nextHandler);
        }

        @Override
        public boolean isOpen() {
            return connection.isOpen();
        }
    }

}
