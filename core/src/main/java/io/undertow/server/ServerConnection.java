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
     *
     * WARNING: do not attempt to write to the current exchange until the out of band
     * exchange has been fully written. Doing so may have unexpected results.
     *
     * TODO: this needs more thought.
     *
     * @return The out of band exchange.
     * @param exchange The current exchange
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

    /**
     * Returns the actual address of the remote connection. This will not take things like X-Forwarded-for
     * into account.
     * @return The address of the remote peer
     */
    SocketAddress getPeerAddress();

    /**
     * Returns the actual address of the remote connection. This will not take things like X-Forwarded-for
     * into account.
     *
     * @param type The type of address to return
     * @param <A> The address type
     * @return The remote endpoint address
     */
    <A extends SocketAddress> A getPeerAddress(Class<A> type);

    SocketAddress getLocalAddress();

    <A extends SocketAddress> A getLocalAddress(Class<A> type);

    OptionMap getUndertowOptions();

    int getBufferSize();

    /**
     * Gets SSL information about the connection. This could represent the actual
     * client connection, or could be providing SSL information that was provided
     * by a front end proxy.
     *
     * @return SSL information about the connection
     */
    SSLSessionInfo getSslSessionInfo();

    /**
     * Sets the current SSL information. This can be used by handlers to setup SSL
     * information that was provided by a front end proxy.
     *
     * If this is being set of a per request basis then you must ensure that it is either
     * cleared by an exchange completion listener at the end of the request, or is always
     * set for every request. Otherwise it is possible to SSL information to 'leak' between
     * requests.
     *
     * @param sessionInfo The ssl session information
     */
    void setSslSessionInfo(SSLSessionInfo sessionInfo);

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
