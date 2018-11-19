package io.undertow.server.protocol.proxy;

import org.xnio.ChannelListener;
import org.xnio.StreamConnection;

import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.UndertowOptionMap;
import io.undertow.server.OpenListener;
import io.undertow.xnio.protocols.ssl.UndertowXnioSsl;

/**
 * Open listener for proxied connections
 *
 * @author Stuart Douglas
 */
public class ProxyProtocolOpenListener implements ChannelListener<StreamConnection> {
    private final OpenListener openListener;
    private final UndertowXnioSsl ssl;
    private final ByteBufferPool bufferPool;
    private final UndertowOptionMap sslUndertowOptionMap;

    public ProxyProtocolOpenListener(OpenListener openListener, UndertowXnioSsl ssl, ByteBufferPool bufferPool, UndertowOptionMap sslUndertowOptionMap) {
        this.openListener = openListener;
        this.ssl = ssl;
        this.bufferPool = bufferPool;
        this.sslUndertowOptionMap = sslUndertowOptionMap;
    }

    @Override
    public void handleEvent(StreamConnection streamConnection) {
        streamConnection.getSourceChannel().setReadListener(new ProxyProtocolReadListener(streamConnection, openListener, ssl, bufferPool, sslUndertowOptionMap));
        streamConnection.getSourceChannel().wakeupReads();
    }
}
