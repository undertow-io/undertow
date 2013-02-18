package io.undertow.server;

/**
 * Listener interface for events that are run at the completion of a request/response
 * cycle (i.e. when the request has been completely read, and the response has been fully written).
 *
 * At this point it is to late to modify the exchange further.
 *
 * Completion listeners are invoked in reverse order,
 *
 * @author Stuart Douglas
 */
public interface ExchangeCompletionListener {

    void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener);

    interface NextListener {

        void proceed();

    }
}
