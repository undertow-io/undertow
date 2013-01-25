package io.undertow.server.handlers.encoding;

import io.undertow.channels.DeflatingStreamSinkChannel;
import io.undertow.server.ChannelWrapper;
import io.undertow.server.HttpServerExchange;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.StreamSinkChannel;

/**
 * Content coding for 'deflate'
 *
 * @author Stuart Douglas
 */
public class DeflateEncoding implements ContentEncoding {

    @Override
    public void setupContentEncoding(final HttpServerExchange exchange) {
        exchange.addResponseWrapper(new ChannelWrapper<StreamSinkChannel>() {
            @Override
            public StreamSinkChannel wrap(final ChannelFactory<StreamSinkChannel> channelFactory, final HttpServerExchange exchange) {
                return new DeflatingStreamSinkChannel(channelFactory, exchange);
            }
        });
    }
}
