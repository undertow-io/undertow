package io.undertow.ajp;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class AjpOpenListener implements OpenListener {

    private final Pool<ByteBuffer> bufferPool;
    private final int bufferSize;

    private volatile String scheme = "http";

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

    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }

        AjpServerConnection connection = new AjpServerConnection(channel, bufferPool, rootHandler, undertowOptions, bufferSize);
        AjpReadListener readListener = new AjpReadListener(connection, scheme);
        readListener.startRequest();
        channel.getSourceChannel().setReadListener(readListener);
        readListener.handleEvent(channel.getSourceChannel());
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
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("undertowOptions");
        }
        this.undertowOptions = undertowOptions;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(final String scheme) {
        this.scheme = scheme;
    }
}
