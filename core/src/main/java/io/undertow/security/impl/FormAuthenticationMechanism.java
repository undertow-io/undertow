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

import static io.undertow.UndertowMessages.MESSAGES;

/**
 * @author Stuart Douglas
 */
public class FormAuthenticationMechanism implements AuthenticationMechanism {

    public static final String LOCATION_ATTRIBUTE = FormAuthenticationMechanism.class.getName() + ".LOCATION";

    public static final String DEFAULT_POST_LOCATION = "/j_security_check";

    private final String name;
    private final String loginPage;
    private final String errorPage;
    private final String postLocation;
    private final FormParserFactory formParserFactory;
    private final IdentityManager identityManager;

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
            } finally {
                if (outcome == AuthenticationMechanismOutcome.AUTHENTICATED) {
                    handleRedirectBack(exchange);
                    exchange.endExchange();
                }
                return outcome != null ? outcome : AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void handleRedirectBack(final HttpServerExchange exchange) {
        final Session session = Sessions.getSession(exchange);
        if (session != null) {
            final String location = (String) session.removeAttribute(LOCATION_ATTRIBUTE);
            if(location != null) {
                exchange.addDefaultResponseListener(new DefaultResponseListener() {
                    @Override
                    public boolean handleDefaultResponse(final HttpServerExchange exchange) {
                        FormAuthenticationMechanism.sendRedirect(exchange, location);
                        exchange.setStatusCode(StatusCodes.FOUND);
                        exchange.endExchange();
                        return true;
                    }
                });
            }

        }
    }

    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
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
        Session session = Sessions.getOrCreateSession(exchange);
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
