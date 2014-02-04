package io.undertow.client.http;

import io.undertow.conduits.AbstractFixedLengthStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

class ClientFixedLengthStreamSinkConduit extends AbstractFixedLengthStreamSinkConduit {

    private final HttpClientExchange exchange;

    /**
     * Construct a new instance.
     *
     * @param next           the next channel
     * @param contentLength  the content length
     * @param configurable   {@code true} if this instance should pass configuration to the next
     * @param propagateClose {@code true} if this instance should pass close to the next
     * @param exchange
     */
    public ClientFixedLengthStreamSinkConduit(StreamSinkConduit next, long contentLength, boolean configurable, boolean propagateClose, HttpClientExchange exchange) {
        super(next, contentLength, configurable, propagateClose);
        this.exchange = exchange;
    }



    @Override
    protected void channelFinished() {
        exchange.terminateRequest();
    }
}
