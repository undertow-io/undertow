package io.undertow.client;

import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * A client connection provider. This allows the difference between various connection
 * providers to be abstracted away (HTTP, AJP etc).
 *
 * @author Stuart Douglas
 */
public interface ClientProvider {

    Set<String> handlesSchemes();

    void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioWorker worker, XnioSsl ssl, Pool<ByteBuffer> bufferPool, OptionMap options);

    void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioIoThread ioThread, XnioSsl ssl, Pool<ByteBuffer> bufferPool, OptionMap options);

}
