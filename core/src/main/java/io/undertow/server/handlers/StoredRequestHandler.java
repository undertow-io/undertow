package io.undertow.server.handlers;

import io.undertow.conduits.StoredRequestStreamSinkConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSourceConduit;

/**
 * A handler that buffers the full response and attaches it to the exchange. This makes use of
 * {@link io.undertow.conduits.StoredRequestStreamSinkConduit}
 * <p>
 * This will be made available once the request is fully complete, so should generally
 * be read in an {@link io.undertow.server.ExchangeCompletionListener}
 *
 * @author Farid Zakaria
 */
public class StoredRequestHandler implements HttpHandler {

    private final HttpHandler next;

    public StoredRequestHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addRequestWrapper(new ConduitWrapper<StreamSourceConduit>() {
            @Override
            public StreamSourceConduit wrap(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exchange) {
                return new StoredRequestStreamSinkConduit(factory.create(), exchange);
            }
        });
        next.handleRequest(exchange);
    }

}
