package io.undertow.server.session;

import io.undertow.server.HttpServerExchange;

/**
 * Interface that abstracts the process of attaching a session to an exchange. This includes both the HTTP side of
 * attachment such as setting a cookie, as well as actually attaching the session to the exchange for use by later
 * handlers.
 *
 * <p/>
 * Generally this will just set a cookie.
 *
 * @author Stuart Douglas
 */
public interface SessionConfig {

    /**
     * Attaches the session to the exchange. The method should attach the exchange under an attachment key,
     * and should also modify the exchange to allow the session to be re-attached on the next request.
     * <p/>
     * Generally this will involve setting a cookie
     * <p/>
     * Once a session has been attached it must be possible to retrieve it via
     * {@link #getAttachedSession(io.undertow.server.HttpServerExchange)}
     *
     *
     * @param exchange The exchange
     * @param session  The session
     */
    void attachSession(final HttpServerExchange exchange, final Session session);

    /**
     * Clears this session from the exchange, removing the attachment and making any changes to the response necessary,
     * such as clearing cookies.
     *
     * @param exchange The exchange
     * @param session  The session
     */
    void clearSession(final HttpServerExchange exchange, final Session session);

    /**
     * Retrieve an existing session from the exchange. This method is basically just a performance optimisation,
     * and allows the config to stash the session into the exchange as an attachment. Conceptually it should give the
     * same result as looking up the results of {@link #findSessionId(io.undertow.server.HttpServerExchange)} in the
     * session manager.
     *
     * Implementations are required to implement this however,
     *
     * @param exchange the exchange
     * @return The existing session, or null if it has not been attached
     */
    Session getAttachedSession(final HttpServerExchange exchange);

    /**
     * Retrieves a session id of an existing session from an exchange.
     *
     * @param exchange The exchange
     * @return The session id, or null
     */
    String findSessionId(final HttpServerExchange exchange);

    String rewriteUrl(final String originalUrl, final Session session);

}
