package io.undertow.security.impl;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityNotification;
import io.undertow.security.idm.Account;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionManager;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Sessions;
import org.xnio.conduits.StreamSinkConduit;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Authenticator that can be used to configure single sign on.
 *
 * @author Stuart Douglas
 * @author Paul Ferraro
 */
public class SingleSignOnAuthenticationMechanism implements AuthenticationMechanism {

    private static final String SSO_SESSION_ATTRIBUTE = SingleSignOnAuthenticationMechanism.class.getName() + ".SSOID";

    // Use weak references to prevent memory leaks following undeployment
    private final Set<SessionManager> seenSessionManagers = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<SessionManager, Boolean>()));

    private String cookieName = "JSESSIONIDSSO";
    private boolean httpOnly;
    private boolean secure;
    private String domain;
    private String path;
    private final SessionInvalidationListener listener = new SessionInvalidationListener();
    private final ResponseListener responseListener = new ResponseListener();
    private final SingleSignOnManager manager;

    public SingleSignOnAuthenticationMechanism(SingleSignOnManager storage) {
        this.manager = storage;
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        Cookie cookie = exchange.getRequestCookies().get(cookieName);
        if (cookie != null) {
            final SingleSignOn sso = this.manager.findSingleSignOn(cookie.getValue());
            if (sso != null) {
                try {
                    Account verified = securityContext.getIdentityManager().verify(sso.getAccount());
                    if (verified == null) {
                        //we return not attempted here to allow other mechanisms to proceed as normal
                        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
                    }
                    final Session session = getSession(exchange);
                    registerSessionIfRequired(sso, session);
                    securityContext.authenticationComplete(verified, sso.getMechanismName(), false);
                    securityContext.registerNotificationReceiver(new NotificationReceiver() {
                        @Override
                        public void handleNotification(SecurityNotification notification) {
                            if (notification.getEventType() == SecurityNotification.EventType.LOGGED_OUT) {
                                try {
                                    sso.remove(session);
                                    for (Session associatedSession : sso) {
                                        associatedSession.invalidate(null);
                                    }
                                    manager.removeSingleSignOn(sso.getId());
                                } finally {
                                    sso.close();
                                }
                            }
                        }
                    });
                    return AuthenticationMechanismOutcome.AUTHENTICATED;
                } finally {
                    sso.close();
                }
            }
            clearSsoCookie(exchange);
        }
        exchange.addResponseWrapper(responseListener);
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    private void registerSessionIfRequired(SingleSignOn sso, Session session) {
        if (!sso.contains(session)) {
            sso.add(session);
            session.setAttribute(SSO_SESSION_ATTRIBUTE, sso.getId());
            SessionManager manager = session.getSessionManager();
            if (seenSessionManagers.add(manager)) {
                manager.registerSessionListener(listener);
            }
        }
    }

    private void clearSsoCookie(HttpServerExchange exchange) {
        exchange.getResponseCookies().put(cookieName, new CookieImpl(cookieName).setMaxAge(0).setHttpOnly(httpOnly).setSecure(secure).setDomain(domain));
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        return new ChallengeResult(false);
    }

    protected Session getSession(final HttpServerExchange exchange) {
        return Sessions.getOrCreateSession(exchange);
    }

    final class ResponseListener implements ConduitWrapper<StreamSinkConduit> {

        @Override
        public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
            SecurityContext sc = exchange.getSecurityContext();
            Account account = sc.getAuthenticatedAccount();
            if (account != null) {
                SingleSignOn sso = manager.createSingleSignOn(account, sc.getMechanismName());
                try {

                    Session session = getSession(exchange);
                    registerSessionIfRequired(sso, session);
                    exchange.getResponseCookies().put(cookieName, new CookieImpl(cookieName, sso.getId()).setHttpOnly(httpOnly).setSecure(secure).setDomain(domain).setPath(path));
                } finally {
                    sso.close();
                }
            }
            return factory.create();
        }
    }


    final class SessionInvalidationListener implements SessionListener {

        @Override
        public void sessionCreated(Session session, HttpServerExchange exchange) {
        }

        @Override
        public void sessionDestroyed(Session session, HttpServerExchange exchange, SessionDestroyedReason reason) {
            String ssoId = (String) session.getAttribute(SSO_SESSION_ATTRIBUTE);
            if (ssoId != null) {
                SingleSignOn sso = manager.findSingleSignOn(ssoId);
                if (sso != null) {
                    try {
                        sso.remove(session);
                        if (reason == SessionDestroyedReason.INVALIDATED) {
                            for (Session associatedSession : sso) {
                                associatedSession.invalidate(null);
                            }
                            manager.removeSingleSignOn(ssoId);
                        }
                    } finally {
                        sso.close();
                    }
                }
            }
        }

        @Override
        public void attributeAdded(Session session, String name, Object value) {
        }

        @Override
        public void attributeUpdated(Session session, String name, Object newValue, Object oldValue) {
        }

        @Override
        public void attributeRemoved(Session session, String name, Object oldValue) {
        }

        @Override
        public void sessionIdChanged(Session session, String oldSessionId) {
        }
    }


    public String getCookieName() {
        return cookieName;
    }

    public SingleSignOnAuthenticationMechanism setCookieName(String cookieName) {
        this.cookieName = cookieName;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public SingleSignOnAuthenticationMechanism setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public SingleSignOnAuthenticationMechanism setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public SingleSignOnAuthenticationMechanism setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getPath() {
        return path;
    }

    public SingleSignOnAuthenticationMechanism setPath(String path) {
        this.path = path;
        return this;
    }

}
