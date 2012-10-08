package io.undertow.websockets.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import io.undertow.util.AbstractAttachable;
import io.undertow.websockets.WebSocketHandler;
import io.undertow.websockets.WebSocketVersion;

import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.ConnectedStreamChannel;

public final class WebSocketServerConnection extends AbstractAttachable implements ConnectedChannel {
    private final ConnectedStreamChannel channel;
    private final Pool<ByteBuffer> bufferPool;
    private final WebSocketHandler rootHandler;
    private final OptionMap undertowOptions;
    private final Setter<WebSocketServerConnection> closeSetter;
    private final WebSocketVersion version;
    private final String wsUrl;

    WebSocketServerConnection(WebSocketVersion version, String wsUrl, ConnectedStreamChannel channel, final Pool<ByteBuffer> bufferPool, final WebSocketHandler rootHandler, final OptionMap undertowOptions) {
        this.channel = channel;
        this.bufferPool = bufferPool;
        this.rootHandler = rootHandler;
        this.undertowOptions = undertowOptions;
        this.version = version;
        this.wsUrl = wsUrl;
        closeSetter = ChannelListeners.getDelegatingSetter(channel.getCloseSetter(), this);
    }
    /**
     * Get the root WebSockets handler for this connection.
     *
     */
    public WebSocketHandler getRootHandler() {
        return rootHandler;
    }

    /**
     * Return the {@link WebSocketVersion} which is used by this {@link WebSocketServerConnection}.
     * 
     */
    public WebSocketVersion getVersion() {
        return version;
    }

    /**
     * Return the url of the WebSocket endpoint.
     * 
     * @return wsUrl The URL of this WebSocket connection endpoint.
     */
    public String getWebSocketUrl() {
        return wsUrl;
    }

    /**
     * Get the buffer pool for this connection.
     *
     */
    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    /**
     * Get the underlying channel.
     *
     */
    public ConnectedStreamChannel getChannel() {
        return channel;
    }

    @Override
    public Setter<WebSocketServerConnection> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public boolean supportsOption(final Option<?> option) {
        return channel.supportsOption(option);
    }

    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        return channel.getOption(option);
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return channel.setOption(option, value);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public SocketAddress getPeerAddress() {
        return channel.getPeerAddress();
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return channel.getPeerAddress(type);
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return channel.getLocalAddress(type);
    }

    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }
}
