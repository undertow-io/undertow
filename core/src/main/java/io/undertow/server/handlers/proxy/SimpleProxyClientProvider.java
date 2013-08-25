package io.undertow.server.handlers.proxy;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;
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
    public void createProxyClient(HttpServerExchange exchange, ProxyCallback<ProxyClient> callback, long timeout, TimeUnit timeUnit) {
        ProxyClient existing = exchange.getConnection().getAttachment(clientAttachmentKey);
        if (existing != null) {
            if (existing.isOpen()) {
                //this connection already has a client, re-use it
                callback.completed(exchange, existing);
                return;
            } else {
                exchange.getConnection().removeAttachment(clientAttachmentKey);
            }
        }
                client.connect(new ConnectNotifier(callback, exchange), uri, exchange.getIoThread(), exchange.getConnection().getBufferPool(), OptionMap.EMPTY);
    }


    private final class ConnectNotifier implements ClientCallback<ClientConnection> {
        private final ProxyCallback<ProxyClient> callback;
        private final HttpServerExchange exchange;

        private ConnectNotifier(ProxyCallback<ProxyClient> callback, HttpServerExchange exchange) {
            this.callback = callback;
            this.exchange = exchange;
        }

        @Override
        public void completed(final ClientConnection connection) {
            final ServerConnection serverConnection = exchange.getConnection();
            final SimpleProxyClient simpleProxyClient = new SimpleProxyClient(connection);
            //we attach to the connection so it can be re-used
            serverConnection.putAttachment(clientAttachmentKey, simpleProxyClient);
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
            callback.completed(exchange, simpleProxyClient);
        }

        @Override
        public void failed(IOException e) {
            callback.failed(exchange);
        }
    }

    private static final class SimpleProxyClient implements ProxyClient {

        private final ClientConnection connection;

        private SimpleProxyClient(ClientConnection connection) {
            this.connection = connection;
        }

        @Override
        public void getConnection(HttpServerExchange exchange, ProxyCallback<ClientConnection> callback, long timeout, TimeUnit timeUnit) {
            callback.completed(exchange, connection);
        }

        @Override
        public boolean isOpen() {
            return connection.isOpen();
        }
    }

}
