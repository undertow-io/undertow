package io.undertow.server.protocol.proxy;

import io.undertow.connector.ByteBufferPool;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.OpenListener;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;

/**
 * Open listener for proxied connections
 *
 * @author Stuart Douglas
 */
public class ProxyProtocolOpenListener implements ChannelListener<StreamConnection> {
    private final OpenListener openListener;
    private final UndertowXnioSsl ssl;
    private final ByteBufferPool bufferPool;
    private final OptionMap sslOptionMap;

    public ProxyProtocolOpenListener(OpenListener openListener, UndertowXnioSsl ssl, ByteBufferPool bufferPool, OptionMap sslOptionMap) {
        this.openListener = openListener;
        this.ssl = ssl;
        this.bufferPool = bufferPool;
        this.sslOptionMap = sslOptionMap;
    }

    @Override
    public void handleEvent(StreamConnection streamConnection) {
        streamConnection.getSourceChannel().setReadListener(new ProxyProtocolReadListener(streamConnection, openListener, ssl, bufferPool, sslOptionMap));
        streamConnection.getSourceChannel().wakeupReads();
    }
}
