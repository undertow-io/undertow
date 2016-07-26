/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.security.impl;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityNotification;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionManager;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Sessions;

import org.jboss.logging.Logger;
import org.xnio.conduits.StreamSinkConduit;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Authenticator that can be used to configure single sign on.
 *
 * @author Stuart Douglas
 * @author Paul Ferraro
 */
public class SingleSignOnAuthenticationMechanism implements AuthenticationMechanism {

    private static final Logger log = Logger.getLogger(SingleSignOnAuthenticationMechanism.class);

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
    private final SingleSignOnManager singleSignOnManager;
    private final IdentityManager identityManager;

    public SingleSignOnAuthenticationMechanism(SingleSignOnManager storage) {
        this(storage, null);
    }

    public SingleSignOnAuthenticationMechanism(SingleSignOnManager storage, IdentityManager identityManager) {
        this.singleSignOnManager = storage;
        this.identityManager = identityManager;
    }

    @SuppressWarnings("deprecation")
    private IdentityManager getIdentityManager(SecurityContext securityContext) {
        return identityManager != null ? identityManager : securityContext.getIdentityManager();
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        Cookie cookie = exchange.getRequestCookies().get(cookieName);
        if (cookie != null) {
            final String ssoId = cookie.getValue();
            log.tracef("Found SSO cookie %s", ssoId);
            try (final SingleSignOn sso = this.singleSignOnManager.findSingleSignOn(ssoId)) {
                if (sso != null) {
                    if(log.isTraceEnabled()) {
                        log.tracef("SSO session with ID: %s found.", ssoId);
                    }
                    Account verified = getIdentityManager(securityContext).verify(sso.getAccount());
                    if (verified == null) {
                        if(log.isTraceEnabled()) {
                            log.tracef("Account not found. Returning 'not attempted' here.");
                        }
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
                                singleSignOnManager.removeSingleSignOn(sso);
                            }
                        }
                    });
                    log.tracef("Authenticated account %s using SSO", verified.getPrincipal().getName());
                    return AuthenticationMechanismOutcome.AUTHENTICATED;
                }
            }
            clearSsoCookie(exchange);
        }
        exchange.addResponseWrapper(responseListener);
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    private void registerSessionIfRequired(SingleSignOn sso, Session session) {
        if (!sso.contains(session)) {
            if(log.isTraceEnabled()) {
                log.tracef("Session %s added to SSO %s", session.getId(), sso.getId());
            }
            sso.add(session);
        }
        if(session.getAttribute(SSO_SESSION_ATTRIBUTE) == null) {
            if(log.isTraceEnabled()) {
                log.tracef("SSO_SESSION_ATTRIBUTE not found. Creating it with SSO ID %s as value.", sso.getId());
            }
            session.setAttribute(SSO_SESSION_ATTRIBUTE, sso.getId());
            SessionManager manager = session.getSessionManager();
            if (seenSessionManagers.add(manager)) {
                manager.registerSessionListener(listener);
            }
        }
    }

    private void clearSsoCookie(HttpServerExchange exchange) {
        exchange.getResponseCookies().put(cookieName, new CookieImpl(cookieName).setMaxAge(0).setHttpOnly(httpOnly).setSecure(secure).setDomain(domain).setPath(path));
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
                try (SingleSignOn sso = singleSignOnManager.createSingleSignOn(account, sc.getMechanismName())) {
                    Session session = getSession(exchange);
                    registerSessionIfRequired(sso, session);
                    exchange.getResponseCookies().put(cookieName, new CookieImpl(cookieName, sso.getId()).setHttpOnly(httpOnly).setSecure(secure).setDomain(domain).setPath(path));
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
                if(log.isTraceEnabled()) {
                    log.tracef("Removing SSO ID %s from destroyed session %s.", ssoId, session.getId());
                }
                List<Session> sessionsToRemove = new LinkedList<>();
                try (SingleSignOn sso = singleSignOnManager.findSingleSignOn(ssoId)) {
                    if (sso != null) {
                        sso.remove(session);
                        if (reason == SessionDestroyedReason.INVALIDATED) {
                            for (Session associatedSession : sso) {
                                sso.remove(associatedSession);
                                sessionsToRemove.add(associatedSession);
                            }
                        }
                        // If there are no more associated sessions, remove the SSO altogether
                        if (!sso.iterator().hasNext()) {
                            singleSignOnManager.removeSingleSignOn(sso);
                        }
                    }
                }
                // Any consequential session invalidations will trigger this listener recursively,
                // so make sure we don't attempt to invalidate session until after the sso is removed.
                for (Session sessionToRemove : sessionsToRemove) {
                    sessionToRemove.invalidate(null);
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
