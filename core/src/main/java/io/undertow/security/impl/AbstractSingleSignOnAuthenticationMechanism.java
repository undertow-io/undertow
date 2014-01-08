package io.undertow.security.impl;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionManager;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Sessions;
import org.xnio.conduits.StreamSinkConduit;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Authenticator that can be used to configure single sign on.
 *
 * @author Stuart Douglas
 */
public abstract class AbstractSingleSignOnAuthenticationMechanism implements AuthenticationMechanism {

    private static final SecureRandomSessionIdGenerator SECURE_RANDOM_SESSION_ID_GENERATOR = new SecureRandomSessionIdGenerator();

    private static final String SSO_SESSION_ATTRIBUTE = AbstractSingleSignOnAuthenticationMechanism.class.getName() + ".SSOID";

    private final Set<SessionManager> seenSessionManagers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<SessionManager, Boolean>()));

    private String cookieName = "JSESSIONIDSSO";
    private boolean httpOnly;
    private boolean secure;
    private String domain;
    private final SessionInvalidationListener listener = new SessionInvalidationListener();
    private final ResponseListener responseListener = new ResponseListener();

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        Cookie cookie = exchange.getRequestCookies().get(cookieName);
        if (cookie != null) {
            SingleSignOnEntry entry = findSsoEntry(cookie.getValue());
            if (entry != null) {
                registerSessionIfRequired(exchange, entry);
                securityContext.authenticationComplete(entry.getAccount(), entry.getMechanismName(), false);
                return AuthenticationMechanismOutcome.AUTHENTICATED;
            } else {
                clearSsoCookie(exchange);
            }
        }
        exchange.addResponseWrapper(responseListener);
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    protected abstract SingleSignOnEntry findSsoEntry(String ssoId);
    protected abstract void storeSsoEntry(String ssoId, SingleSignOnEntry entry);
    protected abstract void removeSsoEntry(String sso);

    private void registerSessionIfRequired(HttpServerExchange exchange, SingleSignOnEntry entry) {
        Session session = getSession(exchange);
        if (!entry.getSessions().containsKey(session.getId())) {
            entry.getSessions().put(session.getId(), session);
            if (!seenSessionManagers.contains(session.getSessionManager())) {
                session.getSessionManager().registerSessionListener(listener);
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

    private final class ResponseListener implements ConduitWrapper<StreamSinkConduit> {

        @Override
        public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
            SecurityContext sc = exchange.getSecurityContext();
            Account account = sc.getAuthenticatedAccount();
            if(account != null) {
                String ssoId = SECURE_RANDOM_SESSION_ID_GENERATOR.createSessionId();
                SingleSignOnEntry entry = new SingleSignOnEntry(account, sc.getMechanismName());
                registerSessionIfRequired(exchange, entry);
                storeSsoEntry(ssoId, entry);
                exchange.getResponseCookies().put(cookieName, new CookieImpl(cookieName, ssoId).setHttpOnly(httpOnly).setSecure(secure).setDomain(domain));
            }
            return factory.create();
        }
    }


    private final class SessionInvalidationListener implements SessionListener {

        @Override
        public void sessionCreated(Session session, HttpServerExchange exchange) {
        }

        @Override
        public void sessionDestroyed(Session session, HttpServerExchange exchange, SessionDestroyedReason reason) {
            Object sso = session.getAttribute(SSO_SESSION_ATTRIBUTE);
            if (sso != null) {
                SingleSignOnEntry entry = findSsoEntry((String) sso);
                if (entry != null) {
                    entry.getSessions().remove(session.getId());
                    if (reason == SessionDestroyedReason.INVALIDATED) {
                        for(Map.Entry<String, Session> s : entry.getSessions().entrySet()) {
                            s.getValue().invalidate(null);
                        }
                        removeSsoEntry((String) sso);
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

    public AbstractSingleSignOnAuthenticationMechanism setCookieName(String cookieName) {
        this.cookieName = cookieName;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public AbstractSingleSignOnAuthenticationMechanism setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public AbstractSingleSignOnAuthenticationMechanism setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public AbstractSingleSignOnAuthenticationMechanism setDomain(String domain) {
        this.domain = domain;
        return this;
    }
}
