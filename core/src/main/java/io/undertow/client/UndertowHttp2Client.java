package io.undertow.client;

import io.undertow.connector.ByteBufferPool;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Undertow http/2 client class. This class loads {@link ClientProvider} implementations, and uses them to
 * create connections to a target.
 *
 * @author Steve Hu
 */
public class UndertowHttp2Client {
    private final Map<String, ClientProvider> clientProviders;
    private static String DEFAULT_SCHEME = null;
    private static final UndertowHttp2Client INSTANCE = new UndertowHttp2Client();

    private UndertowHttp2Client() {
        this(UndertowHttp2Client.class.getClassLoader());
    }

    private UndertowHttp2Client(final ClassLoader classLoader) {
        ServiceLoader<ClientProvider> providers = ServiceLoader.load(ClientProvider.class, classLoader);
        final Map<String, ClientProvider> map = new HashMap<>();
        for (ClientProvider provider : providers) {
            for (String scheme : provider.handlesSchemes()) {
                map.put(scheme, provider);
            }
        }
        this.clientProviders = Collections.unmodifiableMap(map);
    }

    public IoFuture<ClientConnection> connect(final URI uri, final XnioWorker worker, ByteBufferPool bufferPool, OptionMap options) {
        return connect(uri, worker, null, bufferPool, options);
    }

    public IoFuture<ClientConnection> connect(InetSocketAddress bindAddress, final URI uri, final XnioWorker worker, ByteBufferPool bufferPool, OptionMap options) {
        return connect(bindAddress, uri, worker, null, bufferPool, options);
    }

    public IoFuture<ClientConnection> connect(final URI uri, final XnioWorker worker, XnioSsl ssl, ByteBufferPool bufferPool, OptionMap options) {
        return connect((InetSocketAddress)null, uri, worker, ssl, bufferPool, options);
    }

    public String getScheme(URI uri) {
        if(DEFAULT_SCHEME != null) {
            return DEFAULT_SCHEME;
        } else {
            return uri.getScheme().equalsIgnoreCase("http") ? "h2c" : "h2";
        }
    }
    public IoFuture<ClientConnection> connect(InetSocketAddress bindAddress, final URI uri, final XnioWorker worker, XnioSsl ssl, ByteBufferPool bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(getScheme(uri));
        final FutureResult<ClientConnection> result = new FutureResult<>();
        provider.connect(new ClientCallback<ClientConnection>() {
            @Override
            public void completed(ClientConnection r) {
                result.setResult(r);
            }

            @Override
            public void failed(IOException e) {
                result.setException(e);
            }
        }, bindAddress, uri, worker, ssl, bufferPool, options);
        return result.getIoFuture();
    }

    public IoFuture<ClientConnection> connect(final URI uri, final XnioIoThread ioThread, ByteBufferPool bufferPool, OptionMap options) {
        return connect((InetSocketAddress)null, uri, ioThread, null, bufferPool, options);
    }


    public IoFuture<ClientConnection> connect(InetSocketAddress bindAddress, final URI uri, final XnioIoThread ioThread, ByteBufferPool bufferPool, OptionMap options) {
        return connect(bindAddress, uri, ioThread, null, bufferPool, options);
    }

    public IoFuture<ClientConnection> connect(final URI uri, final XnioIoThread ioThread, XnioSsl ssl, ByteBufferPool bufferPool, OptionMap options) {
        return connect((InetSocketAddress)null, uri, ioThread, ssl, bufferPool, options);
    }

    public IoFuture<ClientConnection> connect(InetSocketAddress bindAddress, final URI uri, final XnioIoThread ioThread, XnioSsl ssl, ByteBufferPool bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(getScheme(uri));
        final FutureResult<ClientConnection> result = new FutureResult<>();
        provider.connect(new ClientCallback<ClientConnection>() {
            @Override
            public void completed(ClientConnection r) {
                result.setResult(r);
            }

            @Override
            public void failed(IOException e) {
                result.setException(e);
            }
        }, bindAddress, uri, ioThread, ssl, bufferPool, options);
        return result.getIoFuture();
    }

    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioWorker worker, ByteBufferPool bufferPool, OptionMap options) {
        connect(listener, uri, worker, null, bufferPool, options);
    }

    public void connect(final ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, final URI uri, final XnioWorker worker, ByteBufferPool bufferPool, OptionMap options) {
        connect(listener, bindAddress, uri, worker, null, bufferPool, options);
    }

    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioWorker worker, XnioSsl ssl, ByteBufferPool bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(getScheme(uri));
        provider.connect(listener, uri, worker, ssl, bufferPool, options);
    }

    public void connect(final ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, final URI uri, final XnioWorker worker, XnioSsl ssl, ByteBufferPool bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(getScheme(uri));
        provider.connect(listener, bindAddress, uri, worker, ssl, bufferPool, options);
    }

    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioIoThread ioThread, ByteBufferPool bufferPool, OptionMap options) {
        connect(listener, uri, ioThread, null, bufferPool, options);
    }


    public void connect(final ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, final URI uri, final XnioIoThread ioThread, ByteBufferPool bufferPool, OptionMap options) {
        connect(listener, bindAddress, uri, ioThread, null, bufferPool, options);
    }

    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioIoThread ioThread, XnioSsl ssl, ByteBufferPool bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(getScheme(uri));
        provider.connect(listener, uri, ioThread, ssl, bufferPool, options);
    }

    public void connect(final ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, final URI uri, final XnioIoThread ioThread, XnioSsl ssl, ByteBufferPool bufferPool, OptionMap options) {
        ClientProvider provider = getClientProvider(getScheme(uri));
        provider.connect(listener, bindAddress, uri, ioThread, ssl, bufferPool, options);
    }

    private ClientProvider getClientProvider(String scheme) {
        ClientProvider provider = clientProviders.get(scheme);
        if (provider == null) {
            throw UndertowClientMessages.MESSAGES.unknownScheme(scheme);
        }
        return provider;
    }

    public static UndertowHttp2Client getInstance() {
        return INSTANCE;
    }

    public static UndertowHttp2Client getInstance(boolean knownHttp2) {
        if(knownHttp2) DEFAULT_SCHEME = "h2c-prior";
        return INSTANCE;
    }

    public static UndertowHttp2Client getInstance(final ClassLoader classLoader) {
        return new UndertowHttp2Client(classLoader);
    }

    public static UndertowHttp2Client getInstance(final ClassLoader classLoader, boolean knownHttp2) {
        if(knownHttp2) DEFAULT_SCHEME = "h2c-prior";
        return new UndertowHttp2Client(classLoader);
    }
}
