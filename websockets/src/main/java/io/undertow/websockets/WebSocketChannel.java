package io.undertow.websockets;

import io.undertow.websockets.frame.WebSocketFrameType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioWorker;
import org.xnio.ChannelListener.Setter;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

public abstract class WebSocketChannel implements ConnectedChannel {

    final AtomicReference<StreamSourceFrameChannel> receiver = new AtomicReference<StreamSourceFrameChannel>();
    final ConcurrentLinkedQueue<StreamSinkFrameChannel> currentSender = new ConcurrentLinkedQueue<StreamSinkFrameChannel>();
    private final ConnectedStreamChannel channel;
    private final WebSocketVersion version;
    private final String wsUrl;
    private final Setter<WebSocketChannel> closeSetter;
    private final PushBackStreamChannel pushBackStreamChannel;

    public WebSocketChannel(final ConnectedStreamChannel channel, WebSocketVersion version, String wsUrl) {
        this.channel = channel;
        this.version = version;
        this.wsUrl = wsUrl;
        closeSetter = ChannelListeners.getDelegatingSetter(channel.getCloseSetter(), this);
        pushBackStreamChannel = new PushBackStreamChannel(channel);
        pushBackStreamChannel.getReadSetter().set(createListener());
    }

    boolean remove(StreamSinkFrameChannel channel) throws IOException {
        if (currentSender.peek() == channel) {
            currentSender.remove(channel);
            channel.flush();
            StreamSinkFrameChannel ch = currentSender.peek();
            ChannelListener<? super StreamSinkFrameChannel> listener = ch.writeSetter.get();
            if (listener != null) {
                listener.handleEvent(ch);
            }
            return true;
        } else {
            currentSender.remove(channel);
        }
        return false;
    }

    
    @Override
    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return channel.getLocalAddress(type);
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return channel.supportsOption(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return channel.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return channel.setOption(option, value);
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public SocketAddress getPeerAddress() {
        return channel.getPeerAddress();
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        return channel.getPeerAddress(type);
    }

    /**
     * Get the request URI scheme. Normally this is one of {@code ws} or {@code wss}.
     *
     * @return the request URI scheme
     */
    public String getRequestScheme() {
        if (getUrl().startsWith("wss:")) {
            return "wss";
        } else {
            return "ws";
        }
    }

    /**
     * Return <code>true</code> if this is handled via WebSocket Secure.
     */
    public boolean isSecure() {
        return getRequestScheme().equals("wss");
    }

    /**
     * Return the URL of the WebSocket endpoint.
     * 
     * @return url The URL of the endpoint
     */
    public String getUrl() {
        return wsUrl;
    }

    /**
     * Return the {@link WebSocketVersion} which is used
     * 
     * @return version The {@link WebSocketVersion} which is in use
     */
    public WebSocketVersion getVersion() {
        return version;
    }

    /**
     * Get the source address of the WebSocket Channel.
     *
     * @return the source address of the WebSocket Channel
     */
    public InetSocketAddress getSourceAddress() {
        return getPeerAddress(InetSocketAddress.class);
    }

    /**
     * Get the destination address of the WebSocket Channel.
     *
     * @return the destination address of the WebSocket Channel
     */
    public InetSocketAddress getDestinationAddress() {
        return getLocalAddress(InetSocketAddress.class);
    }

    
    /**
     * Async receive, returns null if no frame is ready. Otherwise returns a
     * channel that can be used to read the frame contents.
     */
    public StreamSourceFrameChannel receive() {
        if (receiver.get() == null && receiver.getAndSet(create(pushBackStreamChannel)) == null) {
            return receiver.get();
        }
        return null;
    }

    public void close() throws IOException {
        IOException ex = null;
        StreamSourceFrameChannel channel = receiver.get();
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                ex = e;
            }
        }
        for (;;) {
            StreamSinkFrameChannel ch = currentSender.poll();
            if (ch == null) {
                break;
            }
            try {
                ch.close();
            } catch (IOException e) {
                if (ex == null) {
                    ex = e;
                }
            }
        }
        try {
            pushBackStreamChannel.close();
        } catch (IOException e) {
            if (ex == null) {
                ex = e;
            }
        }
        try {
            channel.close();
        } catch (IOException e) {
            if (ex == null) {
                ex = e;
            }
        }
        
        if (ex != null) {
            throw ex;
        }
    }
    
    /**
     * Returns a new frame channel for sending. If this is called multiple times
     * subsequent channels will not be writable until all previous frame have
     * been completed.
     */
    public StreamSinkChannel send(WebSocketFrameType type) {
        StreamSinkFrameChannel ch = create(channel, type);
        boolean o = currentSender.offer(ch);
        assert o;
        return ch;
    }

    public ChannelListener.Setter<? extends WebSocketChannel> getReceiveSetter() {
        return null;
    }

    @Override
    public ChannelListener.Setter<? extends WebSocketChannel> getCloseSetter() {
        return closeSetter;
    }

    protected abstract StreamSourceFrameChannel create(StreamSourceChannel channel);
    
    protected abstract StreamSinkFrameChannel create(StreamSinkChannel channel, WebSocketFrameType type);
    
    protected abstract ChannelListener<PushBackStreamChannel> createListener();

}
