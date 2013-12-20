package io.undertow.util;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;

/**
 * Utility class for working with sessions.
 *
 * @author Stuart Douglas
 */
public class Sessions {

    /**
     * Gets the active session, returning null if one is not present.
     * @param exchange The exchange
     * @return The session
     */
    public static Session getSession(final HttpServerExchange exchange) {
        SessionManager sessionManager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        SessionConfig sessionConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);
        if(sessionManager == null) {
            throw UndertowMessages.MESSAGES.sessionManagerNotFound();
        }
        return sessionManager.getSession(exchange, sessionConfig);
    }

    /**
     * Gets the active session, creating a new one if one does not exist
     * @param exchange The exchange
     * @return The session
     */
    public static Session getOrCreateSession(final HttpServerExchange exchange) {
        SessionManager sessionManager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        SessionConfig sessionConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);
        if(sessionManager == null) {
            throw UndertowMessages.MESSAGES.sessionManagerNotFound();
        }
        Session session = sessionManager.getSession(exchange, sessionConfig);
        if(session == null) {
            session = sessionManager.createSession(exchange, sessionConfig);
        }
        return session;
    }

    private Sessions () {}

}
