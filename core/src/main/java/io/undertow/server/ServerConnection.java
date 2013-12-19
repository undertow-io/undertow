package io.undertow.server;

import io.undertow.util.AbstractAttachable;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * A server connection.
 *
 * @author Stuart Douglas
 */
public abstract class ServerConnection extends AbstractAttachable implements ConnectedChannel  {

    /**
     *
     * @return The connections buffer pool
     */
    public abstract Pool<ByteBuffer> getBufferPool();

    /**
     *
     * @return The connections worker
     */
    public abstract XnioWorker getWorker();

    /**
     *
     * @return The IO thread associated with the connection
     */
    @Override
    public abstract XnioIoThread getIoThread();

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
    public abstract HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange);

    /**
     *
     * @return true if the connection is open
     */
    public abstract boolean isOpen();

    public abstract boolean supportsOption(Option<?> option);

    public abstract <T> T getOption(Option<T> option) throws IOException;

    public abstract <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException;

    public abstract void close() throws IOException;

    /**
     * Returns the actual address of the remote connection. This will not take things like X-Forwarded-for
     * into account.
     * @return The address of the remote peer
     */
    public abstract SocketAddress getPeerAddress();

    /**
     * Returns the actual address of the remote connection. This will not take things like X-Forwarded-for
     * into account.
     *
     * @param type The type of address to return
     * @param <A> The address type
     * @return The remote endpoint address
     */
    public abstract <A extends SocketAddress> A getPeerAddress(Class<A> type);

    public abstract SocketAddress getLocalAddress();

    public abstract <A extends SocketAddress> A getLocalAddress(Class<A> type);

    public abstract OptionMap getUndertowOptions();

    public abstract int getBufferSize();

    /**
     * Gets SSL information about the connection. This could represent the actual
     * client connection, or could be providing SSL information that was provided
     * by a front end proxy.
     *
     * @return SSL information about the connection
     */
    public abstract SSLSessionInfo getSslSessionInfo();

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
    public abstract void setSslSessionInfo(SSLSessionInfo sessionInfo);

    /**
     * Adds a close listener, than will be invoked with the connection is closed
     *
     * @param listener The close listener
     */
    public abstract void addCloseListener(CloseListener listener);

    /**
     * Upgrade the connection, if allowed
     * @return The StreamConnection that should be passed to the upgrade handler
     */
    protected abstract StreamConnection upgradeChannel();

    protected abstract ConduitStreamSinkChannel getSinkChannel();

    protected abstract ConduitStreamSourceChannel getSourceChannel();

    /**
     * Gets the sink conduit that should be used for this request.
     *
     * This allows the connection to apply any per-request conduit wrapping
     * that is required, without adding to the response wrappers array.
     *
     * There is no corresponding method for source conduits, as in general
     * conduits can be directly inserted into the connection after the
     * request has been read.
     *
     * @return The source conduit
     */
    protected abstract StreamSinkConduit getSinkConduit(HttpServerExchange exchange, final StreamSinkConduit conduit);

    protected abstract boolean isUpgradeSupported();

    /**
     * Invoked when the exchange is complete.
     */
    protected abstract void exchangeComplete(HttpServerExchange exchange);

    protected abstract void setUpgradeListener(HttpUpgradeListener upgradeListener);

    public interface CloseListener {

        void closed(final ServerConnection connection);
    }
}
