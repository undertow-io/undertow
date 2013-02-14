package io.undertow.server.handlers.encoding;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSinkConduit;

/**
 * @author Stuart Douglas
 */
public interface ContentEncodingProvider {


    ContentEncodingProvider IDENTITY = new ContentEncodingProvider() {

        private final ConduitWrapper<StreamSinkConduit> CONDUIT_WRAPPER = new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
                return factory.create();
            }
        };

        @Override
        public ConduitWrapper<StreamSinkConduit> getResponseWrapper() {
            return CONDUIT_WRAPPER;
        }
    };

    ConduitWrapper<StreamSinkConduit> getResponseWrapper();

}
