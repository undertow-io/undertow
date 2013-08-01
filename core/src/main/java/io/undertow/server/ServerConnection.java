package io.undertow.server;

import io.undertow.util.Attachable;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * A server connection.
 *
 * @author Stuart Douglas
 */
public interface ServerConnection extends Attachable, ConnectedChannel {

    /**
     *
     * @return The connections buffer pool
     */
    Pool<ByteBuffer> getBufferPool();

    /**
     *
     * @return The connections worker
     */
    XnioWorker getWorker();

    /**
     *
     * @return The IO thread associated with the connection
     */
    @Override
    XnioIoThread getIoThread();

    /**
     * Sends an out of band response, such as a HTTP 100-continue response.
     * @return
     * @param exchange
     */
    HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange);

    /**
     *
     * @return true if the connection is open
     */
    boolean isOpen();

    boolean supportsOption(Option<?> option);

    <T> T getOption(Option<T> option) throws IOException;

    <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException;

    void close() throws IOException;

    SocketAddress getPeerAddress();

    <A extends SocketAddress> A getPeerAddress(Class<A> type);

    SocketAddress getLocalAddress();

    <A extends SocketAddress> A getLocalAddress(Class<A> type);

    OptionMap getUndertowOptions();

    int getBufferSize();

    SSLSessionInfo getSslSessionInfo();

    /**
     * Adds a close listener, than will be invoked with the connection is closed
     *
     * @param listener The close listener
     */
    void addCloseListener(CloseListener listener);

    /**
     * Upgrade the connection, if allowed
     * @return The StreamConnection that should be passed to the upgrade handler
     */
    StreamConnection upgradeChannel();

    ConduitStreamSinkChannel getSinkChannel();

    ConduitStreamSourceChannel getSourceChannel();

    public interface CloseListener {

        void closed(final ServerConnection connection);
    }
}
