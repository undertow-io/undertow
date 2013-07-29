package io.undertow.server.handlers.encoding;

import io.undertow.conduits.GzipStreamSinkConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSinkConduit;

/**
 * Content coding for 'deflate'
 *
 * @author Stuart Douglas
 */
public class GzipEncodingProvider implements ContentEncodingProvider {

    @Override
    public ConduitWrapper<StreamSinkConduit> getResponseWrapper() {
        return new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
                return new GzipStreamSinkConduit(factory, exchange);
            }
        };
    }
}
