package io.undertow.websockets;


import io.undertow.websockets.server.WebSocketServerConnection;

import org.xnio.ChannelListener;
import org.xnio.channels.PushBackStreamChannel;

/**
 * TODO: Implement me!
 * 
 *
 */
public class WebSocketReadListener implements ChannelListener<PushBackStreamChannel> {

    private WebSocketServerConnection connection;


    WebSocketReadListener(){}

    public void handleEvent(final PushBackStreamChannel channel) {
        if (connection == null) {
            throw new IllegalStateException("Connection must be set before start to handle events");
        
        }
    }

    public void setConnection(WebSocketServerConnection connection) {
        if (this.connection != null) {
            throw new IllegalStateException("Connection was set before");
        }
        this.connection = connection;
    }

}
