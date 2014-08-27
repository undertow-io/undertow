package io.undertow.server.session;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

import io.undertow.server.HttpServerExchange;

/**
 * Utility class that maintains the session listeners.
 *
 *
 * @author Stuart Douglas
 */
public class SessionListeners {

    private final List<SessionListener> sessionListeners = new CopyOnWriteArrayList<SessionListener>();

    public void addSessionListener(final SessionListener listener) {
        this.sessionListeners.add(listener);
    }

    public boolean removeSessionListener(final SessionListener listener) {
        return this.sessionListeners.remove(listener);
    }

    public void clear() {
        this.sessionListeners.clear();
    }

    public void sessionCreated(final Session session, final HttpServerExchange exchange) {
        for (SessionListener listener : sessionListeners) {
            listener.sessionCreated(session, exchange);
        }
    }

    public void sessionDestroyed(final Session session, final HttpServerExchange exchange, SessionListener.SessionDestroyedReason reason) {
        // We need to create our own snapshot to safely iterate over a concurrent list in reverse
        List<SessionListener> listeners = new ArrayList<SessionListener>(sessionListeners);
        ListIterator<SessionListener> iterator = listeners.listIterator(listeners.size());
        while (iterator.hasPrevious()) {
            iterator.previous().sessionDestroyed(session, exchange, reason);
        }
    }

    public void attributeAdded(final Session session, final String name, final Object value) {
        for (SessionListener listener : sessionListeners) {
            listener.attributeAdded(session, name, value);
        }

    }

    public void attributeUpdated(final Session session, final String name, final Object newValue, final Object oldValue) {
        for (SessionListener listener : sessionListeners) {
            listener.attributeUpdated(session, name, newValue, oldValue);
        }

    }

    public void attributeRemoved(final Session session, final String name, final Object oldValue) {
        for (SessionListener listener : sessionListeners) {
            listener.attributeRemoved(session, name, oldValue);
        }
    }

    public void sessionIdChanged(final Session session, final String oldSessionId) {
        for (SessionListener listener : sessionListeners) {
            listener.sessionIdChanged(session, oldSessionId);
        }
    }

}
