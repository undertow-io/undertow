package io.undertow.server.handlers;

import java.util.Date;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;

/**
 * Class that adds the Date: header to a HTTP response.
 *
 * The current date string is cached, and is updated every second in a racey
 * manner (i.e. it is possible for two thread to update it at once).
 *
 * @author Stuart Douglas
 */
@Deprecated()
public class DateHandler implements HttpHandler {

    private final HttpHandler next;
    private volatile String cachedDateString;
    private volatile long nextUpdateTime = -1;


    public DateHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        long time = System.nanoTime();
        if(time < nextUpdateTime) {
            exchange.getResponseHeaders().put(Headers.DATE, cachedDateString);
        } else {
            long realTime = System.currentTimeMillis();
            String dateString = DateUtils.toDateString(new Date(realTime));
            cachedDateString = dateString;
            nextUpdateTime = time + 1000000000;
            exchange.getResponseHeaders().put(Headers.DATE, dateString);
        }
        next.handleRequest(exchange);
    }


}
