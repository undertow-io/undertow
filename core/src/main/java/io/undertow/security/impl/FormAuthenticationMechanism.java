package io.undertow.security.impl;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.Executor;

import io.undertow.UndertowLogger;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationState;
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

    private static Charset UTF_8 = Charset.forName("UTF-8");

    public static final String LOCATION_COOKIE = "FORM_AUTH_ORIGINAL_URL";

    private final String loginPage;
    private final String errorPage;
    private final String postLocation;

    public FormAuthenticationMechanism(final String loginPage, final String errorPage) {
        this.loginPage = loginPage;
        this.errorPage = errorPage;
        postLocation = "/j_security_check";
    }

    public FormAuthenticationMechanism(final String loginPage, final String errorPage, final String postLocation) {
        this.loginPage = loginPage;
        this.errorPage = errorPage;
        this.postLocation = postLocation;
    }

    @Override
    public IoFuture<AuthenticationMechanismResult> authenticate(final HttpServerExchange exchange, final IdentityManager identityManager, final Executor handOffExecutor) {
        if (exchange.getRequestURI().endsWith(postLocation) && exchange.getRequestMethod().equals(Methods.POST)) {
            ConcreteIoFuture<AuthenticationMechanismResult> result = new ConcreteIoFuture<AuthenticationMechanismResult>();
            handOffExecutor.execute(new FormAuthRunnable(exchange, identityManager, result));
            return result;
        } else {
            return new FinishedIoFuture<AuthenticationMechanismResult>(new AuthenticationMechanismResult(AuthenticationMechanismOutcome.NOT_ATTEMPTED));
        }
    }


    private class FormAuthRunnable implements Runnable {
        final HttpServerExchange exchange;
        final IdentityManager identityManager;
        final ConcreteIoFuture<AuthenticationMechanismResult> result;

        private FormAuthRunnable(final HttpServerExchange exchange, final IdentityManager identityManager, final ConcreteIoFuture<AuthenticationMechanismResult> result) {
            this.exchange = exchange;
            this.identityManager = identityManager;
            this.result = result;
        }


        @Override
        public void run() {
            final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
            if (parser == null) {
                UndertowLogger.REQUEST_LOGGER.debug("Could not authenticate as no form parser is present");
                result.setResult(new AuthenticationMechanismResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED));
                return;
            }

            try {
                final FormData data = parser.parse().get();
                final FormData.FormValue jUsername = data.getFirst("j_username");
                final FormData.FormValue jPassword = data.getFirst("j_password");
                if (jUsername == null || jPassword == null) {
                    UndertowLogger.REQUEST_LOGGER.debug("Could not authenticate as username or password was not present in the posted result");
                    result.setResult(new AuthenticationMechanismResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED));
                    return;
                }
                final String userName = jUsername.getValue();
                final String password = jPassword.getValue();
                AuthenticationMechanismResult authResult = null;
                PasswordCredential credential = new PasswordCredential(password.toCharArray());
                try {
                    Account account = identityManager.lookupAccount(userName);
                    if (account != null && identityManager.verifyCredential(account, credential)) {
                        authResult = new AuthenticationMechanismResult(new UndertowPrincipal(account), account, true);
                    }
                } finally {
                    if (authResult != null && authResult.getOutcome() == AuthenticationMechanismOutcome.AUTHENTICATED) {
                        final Map<String, Cookie> cookies = CookieImpl.getRequestCookies(exchange);
                        if (cookies != null && cookies.containsKey(LOCATION_COOKIE)) {
                            exchange.putAttachment(ORIGINAL_URL_LOCATION, cookies.get(LOCATION_COOKIE).getValue());
                            final CookieImpl cookie = new CookieImpl(LOCATION_COOKIE);
                            cookie.setMaxAge(0);
                            CookieImpl.addResponseCookie(exchange, cookie);
                        }
                    }
                    this.result.setResult(authResult != null ? authResult : new AuthenticationMechanismResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED));
                }
            } catch (IOException e) {
                result.setException(e);
            }
        }
    }


    @Override
    public void sendChallenge(final HttpServerExchange exchange) {
        if (exchange.getRequestURI().endsWith(postLocation) && exchange.getRequestMethod().equals(Methods.POST)) {
            final SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
            //if the auth did not succeed we redirect to the error page
            if (context.getAuthenticationState() != AuthenticationState.AUTHENTICATED) {
                sendRedirect(exchange, errorPage);
            }
        } else if (Util.shouldChallenge(exchange)) {
            //we need to store the URL
            CookieImpl.addResponseCookie(exchange, new CookieImpl(LOCATION_COOKIE, exchange.getRequestURI()));
            sendRedirect(exchange, loginPage);
        }
    }

    static void sendRedirect(final HttpServerExchange exchange, final String location) {
        exchange.setResponseCode(302);
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
        return "FormAuthenticationMechanism";
    }
}
