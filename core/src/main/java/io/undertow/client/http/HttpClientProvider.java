package io.undertow.client.http;

import io.undertow.UndertowMessages;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientProvider;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class HttpClientProvider implements ClientProvider {

    @Override
    public Set<String> handlesSchemes() {
        return new HashSet<String>(Arrays.asList(new String[]{"http", "https"}));
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioWorker worker, final XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        if (uri.getScheme().equals("https")) {
            if (ssl == null) {
                throw UndertowMessages.MESSAGES.sslWasNull();
            }

            ssl.openSslConnection(worker, new InetSocketAddress(uri.getHost(), uri.getPort()), createOpenListener(listener, uri, ssl, bufferPool, options), options).addNotifier(createNotifier(listener), null);
        }
        worker.openStreamConnection(new InetSocketAddress(uri.getHost(), uri.getPort()), createOpenListener(listener, uri, ssl, bufferPool, options), options).addNotifier(createNotifier(listener), null);
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioIoThread ioThread, final XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        if (uri.getScheme().equals("https")) {
            if (ssl == null) {
                throw UndertowMessages.MESSAGES.sslWasNull();
            }
            ssl.openSslConnection(ioThread, new InetSocketAddress(uri.getHost(), uri.getPort()), createOpenListener(listener, uri, ssl, bufferPool, options), options).addNotifier(createNotifier(listener), null);
        } else {
            ioThread.openStreamConnection(new InetSocketAddress(uri.getHost(), uri.getPort()), createOpenListener(listener, uri, ssl, bufferPool, options), options).addNotifier(createNotifier(listener), null);
        }
    }

    private IoFuture.Notifier<StreamConnection, Object> createNotifier(final ClientCallback<ClientConnection> listener) {
        return new IoFuture.Notifier<StreamConnection, Object>() {
            @Override
            public void notify(IoFuture<? extends StreamConnection> ioFuture, Object o) {
                if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                    listener.failed(ioFuture.getException());
                }
            }
        };
    }

    private ChannelListener<StreamConnection> createOpenListener(final ClientCallback<ClientConnection> listener, final URI uri, final XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        return new ChannelListener<StreamConnection>() {
            @Override
            public void handleEvent(StreamConnection connection) {
                handleConnected(connection, listener, uri, ssl, bufferPool, options);
            }
        };
    }


    private void handleConnected(StreamConnection connection, ClientCallback<ClientConnection> listener, URI uri, XnioSsl ssl, Pool<ByteBuffer> bufferPool, OptionMap options) {
        listener.completed(new HttpClientConnection(connection, options, bufferPool));
    }


}
