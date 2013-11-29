package io.undertow.server;

import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public interface OpenListener extends ChannelListener<StreamConnection> {

    HttpHandler getRootHandler();

    void setRootHandler(HttpHandler rootHandler);

    OptionMap getUndertowOptions();

    void setUndertowOptions(OptionMap undertowOptions);

    Pool<ByteBuffer> getBufferPool();
}
