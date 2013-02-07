package io.undertow.server.handlers.encoding;

import io.undertow.conduits.DeflatingStreamSinkConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSinkConduit;

/**
 * Content coding for 'deflate'
 *
 * @author Stuart Douglas
 */
public class DeflateEncoding implements ContentEncoding {

    @Override
    public void setupContentEncoding(final HttpServerExchange exchange) {
        exchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> channelFactory, final HttpServerExchange exchange) {
                return new DeflatingStreamSinkConduit(channelFactory, exchange);
            }
        });
    }
}
