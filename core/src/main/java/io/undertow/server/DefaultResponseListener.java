package io.undertow.server;

/**
 * Listener interface for default response handlers. These are handlers that generate default content
 * such as error pages.
 *
 * @author Stuart Douglas
 */
public interface DefaultResponseListener {

    /**
     *
     * @param exchange The exchange
     * @return true if this listener is generating a default response.
     */
    boolean handleDefaultResponse(final HttpServerExchange exchange);
}
