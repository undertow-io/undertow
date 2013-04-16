package io.undertow.util;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import org.xnio.conduits.Conduit;

/**
 * @author Stuart Douglas
 */
public class WrapperConduitFactory<T extends Conduit> implements ConduitFactory<T> {

    private final HttpServerExchange exchange;
    private final ConduitWrapper<T>[] wrappers;
    private final int wrapperCount;
    private int position;
    private T first;


    public WrapperConduitFactory(ConduitWrapper<T>[] wrappers, int wrapperCount, T first, HttpServerExchange exchange) {
        this.wrappers = wrappers;
        this.wrapperCount = wrapperCount;
        this.exchange = exchange;
        this.position = wrapperCount - 1;
        this.first = first;
    }

    @Override
    public T create() {
        if (position == -1) {
            return first;
        } else {
            return wrappers[position--].wrap(this, exchange);
        }
    }
}
