/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.security.impl;

import java.io.IOException;
import java.util.Map;

import io.undertow.UndertowLogger;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

import static io.undertow.UndertowMessages.MESSAGES;
import static io.undertow.util.StatusCodes.TEMPORARY_REDIRECT;

/**
 * @author Stuart Douglas
 */
public class FormAuthenticationMechanism implements AuthenticationMechanism {

    public static final String LOCATION_COOKIE = "FORM_AUTH_ORIGINAL_URL";

    private final String name;
    private final String loginPage;
    private final String errorPage;
    private final String postLocation;

    public FormAuthenticationMechanism(final String name, final String loginPage, final String errorPage) {
        this.name = name;
        this.loginPage = loginPage;
        this.errorPage = errorPage;
        postLocation = "/j_security_check";
    }

    public FormAuthenticationMechanism(final String name, final String loginPage, final String errorPage, final String postLocation) {
        this.name = name;
        this.loginPage = loginPage;
        this.errorPage = errorPage;
        this.postLocation = postLocation;
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange,
                                                       final SecurityContext securityContext) {
        if (exchange.getRequestURI().endsWith(postLocation) && exchange.getRequestMethod().equals(Methods.POST)) {
            return runFormAuth(exchange, securityContext);
        } else {
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }
    }

    public AuthenticationMechanismOutcome runFormAuth(final HttpServerExchange exchange, final SecurityContext securityContext) {
        final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
        if (parser == null) {
            UndertowLogger.REQUEST_LOGGER.debug("Could not authenticate as no form parser is present");
            // TODO - May need a better error signaling mechanism here to prevent repeated attempts.
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        try {
            final FormData data = parser.parseBlocking();
            final FormData.FormValue jUsername = data.getFirst("j_username");
            final FormData.FormValue jPassword = data.getFirst("j_password");
            if (jUsername == null || jPassword == null) {
                UndertowLogger.REQUEST_LOGGER.debug("Could not authenticate as username or password was not present in the posted result");
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
            final String userName = jUsername.getValue();
            final String password = jPassword.getValue();
            AuthenticationMechanismOutcome outcome = null;
            PasswordCredential credential = new PasswordCredential(password.toCharArray());
            try {
                IdentityManager identityManager = securityContext.getIdentityManager();
                Account account = identityManager.verify(userName, credential);
                if (account != null) {
                    securityContext.authenticationComplete(account, name);
                    outcome = AuthenticationMechanismOutcome.AUTHENTICATED;
                } else {
                    securityContext.authenticationFailed(MESSAGES.authenticationFailed(userName), name);
                }
            } finally {
                if (outcome == AuthenticationMechanismOutcome.AUTHENTICATED) {
                    handleRedirectBack(exchange);
                }
                return outcome != null ? outcome : AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void handleRedirectBack(final HttpServerExchange exchange) {
        final Map<String, Cookie> cookies = CookieImpl.getRequestCookies(exchange);
        if (cookies != null && cookies.containsKey(LOCATION_COOKIE)) {
            final String location = cookies.get(LOCATION_COOKIE).getValue();
            exchange.addDefaultResponseListener(new DefaultResponseListener() {
                @Override
                public boolean handleDefaultResponse(final HttpServerExchange exchange) {
                    FormAuthenticationMechanism.sendRedirect(exchange, location);
                    exchange.endExchange();
                    return true;
                }
            });

            final CookieImpl cookie = new CookieImpl(LOCATION_COOKIE);
            cookie.setMaxAge(0);
            CookieImpl.addResponseCookie(exchange, cookie);
        }
    }

    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        if (exchange.getRequestURI().endsWith(postLocation) && exchange.getRequestMethod().equals(Methods.POST)) {
            // This method would no longer be called if authentication had already occurred.
            Integer code = servePage(exchange, errorPage);
            return new ChallengeResult(true, code);
        } else {
            // we need to store the URL
            storeInitialLocation(exchange);
            // TODO - Rather than redirecting, in order to make this mechanism compatible with the other mechanisms we need to
            // return the actual error page not a redirect.
            Integer code = servePage(exchange, loginPage);
            return new ChallengeResult(true, code);
        }
    }

    protected void storeInitialLocation(final HttpServerExchange exchange) {
        CookieImpl.addResponseCookie(exchange, new CookieImpl(LOCATION_COOKIE, exchange.getRequestURI()));
    }

    protected Integer servePage(final HttpServerExchange exchange, final String location) {
        sendRedirect(exchange, location);
        return TEMPORARY_REDIRECT;
    }


    static void sendRedirect(final HttpServerExchange exchange, final String location) {
        String host = exchange.getRequestHeaders().getFirst(Headers.HOST);
        if (host == null) {
            host = exchange.getDestinationAddress().getAddress().getHostAddress();
        }
        // TODO - String concatenation to construct URLS is extremely error prone - switch to a URI which will better handle this.
        String loc = exchange.getRequestScheme() + "://" + host + location;
        exchange.getResponseHeaders().put(Headers.LOCATION, loc);
    }
}
