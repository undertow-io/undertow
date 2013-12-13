package io.undertow.server.protocol.http;

import io.undertow.conduits.AbstractFixedLengthStreamSinkConduit;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import org.xnio.conduits.StreamSinkConduit;

/**
 * @author Stuart Douglas
 */
public class ServerFixedLengthStreamSinkConduit extends AbstractFixedLengthStreamSinkConduit {

    private HttpServerExchange exchange;

    /**
     * Construct a new instance.
     *
     * @param next           the next channel
     * @param configurable   {@code true} if this instance should pass configuration to the next
     * @param propagateClose {@code true} if this instance should pass close to the next
     */
    public ServerFixedLengthStreamSinkConduit(StreamSinkConduit next, boolean configurable, boolean propagateClose) {
        super(next, 1, configurable, propagateClose);
    }

    void reset(long contentLength, HttpServerExchange exchange) {
        this.exchange = exchange;
        super.reset(contentLength, !exchange.isPersistent());
    }

    @Override
    protected void channelFinished() {
        Connectors.terminateResponse(exchange);
    }
}
