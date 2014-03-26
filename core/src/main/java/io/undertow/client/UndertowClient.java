package io.undertow.client;

import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Undertow client class. This class loads {@link ClientProvider} implementations, and uses them to
 * create connections to a target.
 *
 * @author Stuart Douglas
 */
public final class UndertowClient {

    private final Map<String, ClientProvider> clientProviders;

    private static final UndertowClient INSTANCE = new UndertowClient();

    private UndertowClient() {
        this(UndertowClient.class.getClassLoader());
    }

    private UndertowClient(final ClassLoader classLoader) {
        ServiceLoader<ClientProvider> providers = ServiceLoader.load(ClientProvider.class, classLoader);
        final Map<String, ClientProvider> map = new HashMap<String, ClientProvider>();
        for (ClientProvider provider : providers) {
            for (String scheme : provider.handlesSchemes()) {
                map.put(scheme, provider);
            }
        }
        this.clientProviders = Collections.unmodifiableMap(map);
    }
    public IoFuture<ClientConnection> connect(final URI uri, final XnioWorker worker, Pool<ByteBuffer> bufferPool, OptionMap options) {
        return connect(uri, worker, null, bufferPool, options);
    }

    public IoFuture<ClientConnection> connect(final URI uri, final XnioWorker worker, XnioSsl ssl, Pool<ByteBuffer> bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(uri);
        final FutureResult<ClientConnection> result = new FutureResult<ClientConnection>();
        provider.connect(new ClientCallback<ClientConnection>() {
            @Override
            public void completed(ClientConnection r) {
                result.setResult(r);
            }

            @Override
            public void failed(IOException e) {
                result.setException(e);
            }
        }, uri, worker, ssl, bufferPool, options);
        return result.getIoFuture();
    }

    public IoFuture<ClientConnection> connect(final URI uri, final XnioIoThread ioThread, Pool<ByteBuffer> bufferPool, OptionMap options) {
        return connect(uri, ioThread, null, bufferPool, options);
    }

    public IoFuture<ClientConnection> connect(final URI uri, final XnioIoThread ioThread, XnioSsl ssl, Pool<ByteBuffer> bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(uri);
        final FutureResult<ClientConnection> result = new FutureResult<ClientConnection>();
        provider.connect(new ClientCallback<ClientConnection>() {
            @Override
            public void completed(ClientConnection r) {
                result.setResult(r);
            }

            @Override
            public void failed(IOException e) {
                result.setException(e);
            }
        }, uri, ioThread, ssl, bufferPool, options);
        return result.getIoFuture();
    }

    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioWorker worker, Pool<ByteBuffer> bufferPool, OptionMap options) {
        connect(listener, uri, worker, null, bufferPool, options);
    }

    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioWorker worker, XnioSsl ssl, Pool<ByteBuffer> bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(uri);
        provider.connect(listener, uri, worker, ssl, bufferPool, options);
    }

    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioIoThread ioThread, Pool<ByteBuffer> bufferPool, OptionMap options) {
        connect(listener, uri, ioThread, null, bufferPool, options);
    }
    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioIoThread ioThread, XnioSsl ssl, Pool<ByteBuffer> bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(uri);
        provider.connect(listener, uri, ioThread, ssl, bufferPool, options);
    }

    private ClientProvider getClientProvider(URI uri) {
        ClientProvider provider = clientProviders.get(uri.getScheme());
        if (provider == null) {
            throw UndertowClientMessages.MESSAGES.unknownScheme(uri);
        }
        return provider;
    }

    public static UndertowClient getInstance() {
        return INSTANCE;
    }

    public static UndertowClient getInstance(final ClassLoader classLoader) {
        return new UndertowClient(classLoader);
    }

}
