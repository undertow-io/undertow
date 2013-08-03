package io.undertow.server.handlers.proxy;

import io.undertow.client.HttpClient;
import io.undertow.client.HttpClientConnection;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;
import io.undertow.util.SameThreadExecutor;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Simple proxy client provider. This provider simply proxies to another server, using a a one to one
 * connection strategy.
 *
 *
 * @author Stuart Douglas
 */
public class SimpleProxyClientProvider implements ProxyClientProvider {

    private final SocketAddress destination;
    private final AttachmentKey<ProxyClient> clientAttachmentKey = AttachmentKey.create(ProxyClient.class);

    public SimpleProxyClientProvider(SocketAddress destination) {
        this.destination = destination;
    }

    @Override
    public void createProxyClient(final HttpServerExchange exchange, final HttpHandler nextHandler, final long timeout, final TimeUnit timeUnit) {
        ProxyClient existing = exchange.getConnection().getAttachment(clientAttachmentKey);
        if(existing != null) {
            //this connection already has a client, re-use it
            exchange.putAttachment(CLIENT, existing);
            exchange.dispatch(SameThreadExecutor.INSTANCE, nextHandler);
            return;
        }
        exchange.dispatch(SameThreadExecutor.INSTANCE, new Runnable() {
            @Override
            public void run() {
                HttpClient client = HttpClient.create(exchange.getConnection().getWorker(), OptionMap.EMPTY);
                client.connect(exchange.getIoThread(), destination, OptionMap.EMPTY).addNotifier(new ConnectNotifier(nextHandler), exchange);
            }
        });
    }


    private final class ConnectNotifier extends IoFuture.HandlingNotifier<HttpClientConnection, HttpServerExchange> {

        private final HttpHandler next;

        private ConnectNotifier(HttpHandler next) {
            this.next = next;
        }

        public void handleCancelled(final HttpServerExchange exchange) {
            try {
                if (!exchange.isResponseStarted()) {
                    exchange.setResponseCode(500);
                }
            } finally {
                exchange.endExchange();
            }
        }

        public void handleFailed(final IOException exception, final HttpServerExchange exchange) {
            exchange.putAttachment(THROWABLE, exception);
            exchange.dispatch(SameThreadExecutor.INSTANCE, next);
        }

        public void handleDone(final HttpClientConnection connection, final HttpServerExchange exchange) {
            final ServerConnection serverConnection = exchange.getConnection();
            final SimpleProxyClient simpleProxyClient = new SimpleProxyClient(connection);
            //we attach to the connection so it can be re-used
            serverConnection.putAttachment(clientAttachmentKey, simpleProxyClient);
            exchange.putAttachment(CLIENT, simpleProxyClient);
            serverConnection.addCloseListener(new ServerConnection.CloseListener() {
                @Override
                public void closed(ServerConnection connection) {
                    IoUtils.safeClose(serverConnection);
                }
            });
            exchange.dispatch(SameThreadExecutor.INSTANCE, next);
        }
    };

    private static final class SimpleProxyClient implements ProxyClient {

        private final HttpClientConnection connection;

        private SimpleProxyClient(HttpClientConnection connection) {
            this.connection = connection;
        }

        @Override
        public void getConnection(HttpServerExchange exchange, HttpHandler nextHandler, long timeout, TimeUnit timeUnit) {
            exchange.putAttachment(CONNECTION, connection);
            exchange.dispatch(SameThreadExecutor.INSTANCE, nextHandler);
        }
    }

}
