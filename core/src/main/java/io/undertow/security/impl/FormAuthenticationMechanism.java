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

import static io.undertow.util.StatusCodes.CODE_307;
import io.undertow.UndertowLogger;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;

import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;

/**
 * @author Stuart Douglas
 */
public class FormAuthenticationMechanism implements AuthenticationMechanism {

    /**
     * When an authentication is successful the original URL is stored in the this attachment,
     * allowing a later handler to do a redirect if desired.
     */
    public static final AttachmentKey<String> ORIGINAL_URL_LOCATION = AttachmentKey.create(String.class);

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
    public IoFuture<AuthenticationMechanismOutcome> authenticate(final HttpServerExchange exchange,
            final SecurityContext securityContext, final Executor handOffExecutor) {
        if (exchange.getRequestURI().endsWith(postLocation) && exchange.getRequestMethod().equals(Methods.POST)) {
            ConcreteIoFuture<AuthenticationMechanismOutcome> result = new ConcreteIoFuture<AuthenticationMechanismOutcome>();
            handOffExecutor.execute(new FormAuthRunnable(exchange, securityContext, result));
            return result;
        } else {
            return new FinishedIoFuture<AuthenticationMechanismOutcome>(AuthenticationMechanismOutcome.NOT_ATTEMPTED);
        }
    }


    private class FormAuthRunnable implements Runnable {
        final HttpServerExchange exchange;
        final SecurityContext securityContext;
        final ConcreteIoFuture<AuthenticationMechanismOutcome> result;

        private FormAuthRunnable(final HttpServerExchange exchange, final SecurityContext securityContext, final ConcreteIoFuture<AuthenticationMechanismOutcome> result) {
            this.exchange = exchange;
            this.securityContext = securityContext;
            this.result = result;
        }


        @Override
        public void run() {
            final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
            if (parser == null) {
                UndertowLogger.REQUEST_LOGGER.debug("Could not authenticate as no form parser is present");
                // TODO - May need a better error signaling mechanism here to prevent repeated attempts.
                result.setResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED);
                return;
            }

            try {
                final FormData data = parser.parse().get();
                final FormData.FormValue jUsername = data.getFirst("j_username");
                final FormData.FormValue jPassword = data.getFirst("j_password");
                if (jUsername == null || jPassword == null) {
                    UndertowLogger.REQUEST_LOGGER.debug("Could not authenticate as username or password was not present in the posted result");
                    result.setResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED);
                    return;
                }
                final String userName = jUsername.getValue();
                final String password = jPassword.getValue();
                AuthenticationMechanismOutcome outcome = null;
                PasswordCredential credential = new PasswordCredential(password.toCharArray());
                try {
                    IdentityManager identityManager = securityContext.getIdentityManager();
                    Account account = identityManager.verify(userName, credential);
                    if (account != null) {
                        securityContext.authenticationComplete(account, name, true);
                        outcome = AuthenticationMechanismOutcome.AUTHENTICATED;
                    }
                } finally {
                    if (outcome == AuthenticationMechanismOutcome.AUTHENTICATED) {
                        final Map<String, Cookie> cookies = CookieImpl.getRequestCookies(exchange);
                        if (cookies != null && cookies.containsKey(LOCATION_COOKIE)) {
                            exchange.putAttachment(ORIGINAL_URL_LOCATION, cookies.get(LOCATION_COOKIE).getValue());
                            final CookieImpl cookie = new CookieImpl(LOCATION_COOKIE);
                            cookie.setMaxAge(0);
                            CookieImpl.addResponseCookie(exchange, cookie);
                        }
                    }
                    this.result.setResult(outcome != null ? outcome : AuthenticationMechanismOutcome.NOT_AUTHENTICATED);
                }
            } catch (IOException e) {
                result.setException(e);
            }
        }
    }

    public IoFuture<ChallengeResult> sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext,
            final Executor handOffExecutor) {
        if (exchange.getRequestURI().endsWith(postLocation) && exchange.getRequestMethod().equals(Methods.POST)) {
            // This method would no longer be called if authentication had already occurred.
            sendRedirect(exchange, errorPage);
            return new FinishedIoFuture<ChallengeResult>(new ChallengeResult(true, CODE_307));
        } else {
            // we need to store the URL
            CookieImpl.addResponseCookie(exchange, new CookieImpl(LOCATION_COOKIE, exchange.getRequestURI()));
            // TODO - Rather than redirecting, in order to make this mechanism compatible with the other mechanisms we need to
            // return the actual error page not a redirect.
            sendRedirect(exchange, loginPage);
            return new FinishedIoFuture<ChallengeResult>(new ChallengeResult(true, CODE_307));
        }
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

    @Override
    public String getName() {
        return name;
    }
}
