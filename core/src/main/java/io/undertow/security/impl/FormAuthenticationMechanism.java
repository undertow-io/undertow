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

import io.undertow.UndertowLogger;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.session.Session;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.RedirectBuilder;
import io.undertow.util.Sessions;
import io.undertow.util.StatusCodes;

import java.io.IOException;
import java.io.UncheckedIOException;

import static io.undertow.UndertowMessages.MESSAGES;

/**
 * @author Stuart Douglas
 */
public class FormAuthenticationMechanism implements AuthenticationMechanism {

    public static final String LOCATION_ATTRIBUTE = FormAuthenticationMechanism.class.getName() + ".LOCATION";
    public static final String DEFAULT_POST_LOCATION = "/j_security_check";
    protected static final String ORIGINAL_SESSION_TIMEOUT = "io.undertow.servlet.form.auth.orig.session.timeout";;
    private final String name;
    private final String loginPage;
    private final String errorPage;
    private final String postLocation;
    private final FormParserFactory formParserFactory;
    private final IdentityManager identityManager;

    /**
     * If the authentication process creates a session, this is the maximum session timeout (in seconds) during the
     * authentication process. Once authentication is complete, the default session timeout will apply. Sessions that
     * exist before the authentication process starts will retain their original session timeout throughout.
     */
    protected final int authenticationSessionTimeout = 120;

    public FormAuthenticationMechanism(final String name, final String loginPage, final String errorPage) {
        this(FormParserFactory.builder().build(), name, loginPage, errorPage);
    }

    public FormAuthenticationMechanism(final String name, final String loginPage, final String errorPage, final String postLocation) {
        this(FormParserFactory.builder().build(), name, loginPage, errorPage, postLocation);
    }

    public FormAuthenticationMechanism(final FormParserFactory formParserFactory, final String name, final String loginPage, final String errorPage) {
        this(formParserFactory, name, loginPage, errorPage, DEFAULT_POST_LOCATION);
    }

    public FormAuthenticationMechanism(final FormParserFactory formParserFactory, final String name, final String loginPage, final String errorPage, final IdentityManager identityManager) {
        this(formParserFactory, name, loginPage, errorPage, DEFAULT_POST_LOCATION, identityManager);
    }

    public FormAuthenticationMechanism(final FormParserFactory formParserFactory, final String name, final String loginPage, final String errorPage, final String postLocation) {
        this(formParserFactory, name, loginPage, errorPage, postLocation, null);
    }

    public FormAuthenticationMechanism(final FormParserFactory formParserFactory, final String name, final String loginPage, final String errorPage, final String postLocation, final IdentityManager identityManager) {
        this.name = name;
        this.loginPage = loginPage;
        this.errorPage = errorPage;
        this.postLocation = postLocation;
        this.formParserFactory = formParserFactory;
        this.identityManager = identityManager;
    }

    @SuppressWarnings("deprecation")
    private IdentityManager getIdentityManager(SecurityContext securityContext) {
        return identityManager != null ? identityManager : securityContext.getIdentityManager();
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange,
                                                       final SecurityContext securityContext) {
        if (exchange.getRequestPath().endsWith(postLocation) && exchange.getRequestMethod().equals(Methods.POST)) {
            return runFormAuth(exchange, securityContext);
        } else {
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }
    }

    public AuthenticationMechanismOutcome runFormAuth(final HttpServerExchange exchange, final SecurityContext securityContext) {
        final FormDataParser parser = formParserFactory.createParser(exchange);
        if (parser == null) {
            UndertowLogger.SECURITY_LOGGER.debug("Could not authenticate as no form parser is present");
            // TODO - May need a better error signaling mechanism here to prevent repeated attempts.
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        Throwable original = null;
        AuthenticationMechanismOutcome retValue = null;
        try {
            final FormData data = parser.parseBlocking();
            final FormData.FormValue jUsername = data.getFirst("j_username");
            final FormData.FormValue jPassword = data.getFirst("j_password");
            if (jUsername == null || jPassword == null) {
                UndertowLogger.SECURITY_LOGGER.debugf("Could not authenticate as username or password was not present in the posted result for %s", exchange);
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
            final String userName = jUsername.getValue();
            final String password = jPassword.getValue();
            AuthenticationMechanismOutcome outcome = null;
            PasswordCredential credential = new PasswordCredential(password.toCharArray());
            try {
                IdentityManager identityManager = getIdentityManager(securityContext);
                Account account = identityManager.verify(userName, credential);
                if (account != null) {
                    securityContext.authenticationComplete(account, name, true);
                    UndertowLogger.SECURITY_LOGGER.debugf("Authenticated user %s using for auth for %s", account.getPrincipal().getName(), exchange);
                    outcome = AuthenticationMechanismOutcome.AUTHENTICATED;
                } else {
                    securityContext.authenticationFailed(MESSAGES.authenticationFailed(userName), name);
                }
            } catch (Throwable t) {
                original = t;
            } finally {
                try {
                    if (outcome == AuthenticationMechanismOutcome.AUTHENTICATED) {
                        handleRedirectBack(exchange);
                        exchange.endExchange();
                    }
                    retValue = outcome != null ? outcome : AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                } catch (Throwable t) {
                    if (original != null) {
                        original.addSuppressed(t);
                    } else {
                        original = t;
                    }
                }
            }
        } catch (IOException e) {
            original = new UncheckedIOException(e);
        }
        if (original != null) {
            if (original instanceof RuntimeException) {
                throw (RuntimeException) original;
            }
            if (original instanceof Error) {
                throw (Error) original;
            }
        }
        return retValue;
    }

    protected void handleRedirectBack(final HttpServerExchange exchange) {
        final Session session = Sessions.getSession(exchange);
        if (session != null) {
            final Integer originalSessionTimeout = (Integer) session.removeAttribute(ORIGINAL_SESSION_TIMEOUT);
            if (originalSessionTimeout != null) {
                session.setMaxInactiveInterval(originalSessionTimeout);
            }
            final String location = (String) session.removeAttribute(LOCATION_ATTRIBUTE);
            if(location != null) {
                exchange.addDefaultResponseListener(new DefaultResponseListener() {
                    @Override
                    public boolean handleDefaultResponse(final HttpServerExchange exchange) {
                        exchange.getResponseHeaders().put(Headers.LOCATION, location);
                        exchange.setStatusCode(StatusCodes.FOUND);
                        exchange.endExchange();
                        return true;
                    }
                });
            }

        }
    }

    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {

        // make sure a request to root context is handled with trailing slash. Otherwise call to j_security_check will not
        // be handled correctly
        if (exchange.getRelativePath().isEmpty()) {
            exchange.getResponseHeaders().put(Headers.LOCATION, RedirectBuilder.redirect(exchange, exchange.getRelativePath() + "/", true));
            return new ChallengeResult(true, StatusCodes.FOUND);
        }
        if (exchange.getRequestPath().endsWith(postLocation) && exchange.getRequestMethod().equals(Methods.POST)) {
            UndertowLogger.SECURITY_LOGGER.debugf("Serving form auth error page %s for %s", loginPage, exchange);
            // This method would no longer be called if authentication had already occurred.
            Integer code = servePage(exchange, errorPage);
            return new ChallengeResult(true, code);
        } else {
            UndertowLogger.SECURITY_LOGGER.debugf("Serving login form %s for %s", loginPage, exchange);

            // we need to store the URL
            storeInitialLocation(exchange);
            // TODO - Rather than redirecting, in order to make this mechanism compatible with the other mechanisms we need to
            // return the actual error page not a redirect.
            Integer code = servePage(exchange, loginPage);
            return new ChallengeResult(true, code);
        }
    }

    protected void storeInitialLocation(final HttpServerExchange exchange) {
        Session session = Sessions.getSession(exchange);
        boolean newSession = false;
        if (session == null) {
            session = Sessions.getOrCreateSession(exchange);
            newSession = true;
        }
        if (newSession) {
            int originalMaxInactiveInterval = session.getMaxInactiveInterval();
            if (originalMaxInactiveInterval > authenticationSessionTimeout) {
                session.setAttribute(ORIGINAL_SESSION_TIMEOUT, session.getMaxInactiveInterval());
                session.setMaxInactiveInterval(authenticationSessionTimeout);
            }
        }
        session.setAttribute(LOCATION_ATTRIBUTE, RedirectBuilder.redirect(exchange, exchange.getRelativePath()));
    }

    protected Integer servePage(final HttpServerExchange exchange, final String location) {
        sendRedirect(exchange, location);
        return StatusCodes.TEMPORARY_REDIRECT;
    }


    static void sendRedirect(final HttpServerExchange exchange, final String location) {
        // TODO - String concatenation to construct URLS is extremely error prone - switch to a URI which will better handle this.
        String loc = exchange.getRequestScheme() + "://" + exchange.getHostAndPort() + location;
        exchange.getResponseHeaders().put(Headers.LOCATION, loc);
    }
}
