package io.undertow.server.protocol.proxy2;

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
public class ProxyProtocolV2OpenListener implements ChannelListener<StreamConnection> {
    private final OpenListener openListener;
    private final UndertowXnioSsl ssl;
    private final ByteBufferPool bufferPool;
    private final OptionMap sslOptionMap;

    public ProxyProtocolV2OpenListener(OpenListener openListener, UndertowXnioSsl ssl, ByteBufferPool bufferPool, OptionMap sslOptionMap) {
        this.openListener = openListener;
        this.ssl = ssl;
        this.bufferPool = bufferPool;
        this.sslOptionMap = sslOptionMap;
    }

    @Override
    public void handleEvent(StreamConnection streamConnection) {
        streamConnection.getSourceChannel().setReadListener(new ProxyProtocolV2ReadListener(streamConnection, openListener, ssl, bufferPool, sslOptionMap));
        streamConnection.getSourceChannel().wakeupReads();
    }
}
