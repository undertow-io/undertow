package io.undertow.ajp;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.channels.ReadTimeoutStreamSourceChannel;
import io.undertow.channels.WriteTimeoutStreamSinkChannel;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.OpenListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.channels.AssembledConnectedStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.SslChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class AjpOpenListener implements OpenListener{

    private final Pool<ByteBuffer> bufferPool;
    private final int bufferSize;

    private volatile HttpHandler rootHandler;

    private volatile OptionMap undertowOptions;

    public AjpOpenListener(final Pool<ByteBuffer> pool, final int bufferSize) {
        this(pool, OptionMap.EMPTY, bufferSize);
    }

    public AjpOpenListener(final Pool<ByteBuffer> pool, final OptionMap undertowOptions, final int bufferSize) {
        this.undertowOptions = undertowOptions;
        this.bufferPool = pool;
        this.bufferSize = bufferSize;
    }

    public void handleEvent(final ConnectedStreamChannel channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        StreamSourceChannel readChannel = channel;
        StreamSinkChannel writeChannel = channel;
        //set read and write timeouts
        if (channel.supportsOption(Options.READ_TIMEOUT)) {
            readChannel = new ReadTimeoutStreamSourceChannel(readChannel);
        }
        if (channel.supportsOption(Options.WRITE_TIMEOUT)) {
            writeChannel = new WriteTimeoutStreamSinkChannel(writeChannel);
        }
        final PushBackStreamChannel pushBackStreamChannel = new PushBackStreamChannel(readChannel);
        SSLSession sslSession = null;
        if (channel instanceof SslChannel) {
            sslSession = ((SslChannel) channel).getSslSession();
        }
        HttpServerConnection connection = new HttpServerConnection(new AssembledConnectedStreamChannel(channel, readChannel, writeChannel), bufferPool, rootHandler, undertowOptions, bufferSize, sslSession);
        AjpReadListener readListener = new AjpReadListener(writeChannel, pushBackStreamChannel, connection);
        pushBackStreamChannel.getReadSetter().set(readListener);
        readListener.handleEvent(pushBackStreamChannel);
    }

    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    public void setRootHandler(final HttpHandler rootHandler) {
        this.rootHandler = rootHandler;
    }

    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    public void setUndertowOptions(final OptionMap undertowOptions) {
        if (undertowOptions == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        this.undertowOptions = undertowOptions;
    }
}
