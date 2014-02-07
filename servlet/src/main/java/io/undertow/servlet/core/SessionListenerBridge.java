package io.undertow.servlet.core;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionListener;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;

import java.util.HashSet;

/**
 * Class that bridges between Undertow native session listeners and servlet ones.
 *
 * @author Stuart Douglas
 */
public class SessionListenerBridge implements SessionListener {

    public static final String IO_UNDERTOW = "io.undertow";
    private final ThreadSetupAction threadSetup;
    private final ApplicationListeners applicationListeners;
    private final ServletContext servletContext;

    public SessionListenerBridge(final ThreadSetupAction threadSetup, final ApplicationListeners applicationListeners, final ServletContext servletContext) {
        this.threadSetup = threadSetup;
        this.applicationListeners = applicationListeners;
        this.servletContext = servletContext;
    }

    @Override
    public void sessionCreated(final Session session, final HttpServerExchange exchange) {
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, true);
        applicationListeners.sessionCreated(httpSession);
    }

    @Override
    public void sessionDestroyed(final Session session, final HttpServerExchange exchange, final SessionDestroyedReason reason) {
        ThreadSetupAction.Handle handle = null;
        try {
            final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, false);
            if (reason == SessionDestroyedReason.TIMEOUT) {
                handle = threadSetup.setup(exchange);
            }
            applicationListeners.sessionDestroyed(httpSession);
            //we make a defensive copy here, as there is no guarantee that the underlying session map
            //is a concurrent map, and as a result a concurrent modification exception may be thrown
            HashSet<String> names = new HashSet<String>(session.getAttributeNames());
            for(String attribute : names) {
                session.removeAttribute(attribute);
            }
        } finally {
            if (handle != null) {
                handle.tearDown();
            }
            ServletRequestContext current = SecurityActions.currentServletRequestContext();
            if (current != null) {
                current.setSession(null);
            }
        }
    }

    @Override
    public void attributeAdded(final Session session, final String name, final Object value) {
        if(name.startsWith(IO_UNDERTOW)) {
            return;
        }
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, false);
        applicationListeners.httpSessionAttributeAdded(httpSession, name, value);
        if (value instanceof HttpSessionBindingListener) {
            ((HttpSessionBindingListener) value).valueBound(new HttpSessionBindingEvent(httpSession, name, value));
        }
    }

    @Override
    public void attributeUpdated(final Session session, final String name, final Object value, final Object old) {
        if(name.startsWith(IO_UNDERTOW)) {
            return;
        }
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, false);
        if (old != value) {
            if (old instanceof HttpSessionBindingListener) {
                ((HttpSessionBindingListener) old).valueUnbound(new HttpSessionBindingEvent(httpSession, name, old));
            }
            applicationListeners.httpSessionAttributeReplaced(httpSession, name, old);
        }
        if (value instanceof HttpSessionBindingListener) {
            ((HttpSessionBindingListener) value).valueBound(new HttpSessionBindingEvent(httpSession, name, value));
        }
    }

    @Override
    public void attributeRemoved(final Session session, final String name, final Object old) {
        if(name.startsWith(IO_UNDERTOW)) {
            return;
        }
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, false);
        if (old != null) {
            applicationListeners.httpSessionAttributeRemoved(httpSession, name, old);
            if (old instanceof HttpSessionBindingListener) {
                ((HttpSessionBindingListener) old).valueUnbound(new HttpSessionBindingEvent(httpSession, name, old));
            }
        }
    }

    @Override
    public void sessionIdChanged(Session session, String oldSessionId) {
    }
}
