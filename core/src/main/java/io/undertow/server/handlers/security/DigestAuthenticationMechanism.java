/*
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
package io.undertow.server.handlers.security;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import org.xnio.IoFuture;

import static io.undertow.UndertowLogger.REQUEST_LOGGER;
import static io.undertow.server.handlers.security.DigestAuthorizationToken.parseHeader;
import static io.undertow.util.Headers.AUTHENTICATION_INFO;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.DIGEST;
import static io.undertow.util.Headers.NEXT_NONCE;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.CODE_401;
import static io.undertow.util.WorkerDispatcher.dispatch;

/**
 * {@link HttpHandler} to handle HTTP Digest authentication, both according to RFC-2617 and draft update to allow additional
 * algorithms to be used.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DigestAuthenticationMechanism implements AuthenticationMechanism {

    private static final String DIGEST_PREFIX = DIGEST + " ";
    private static final int PREFIX_LENGTH = DIGEST_PREFIX.length();
    private static final String OPAQUE_VALUE = "00000000000000000000000000000000";
    private static final byte COLON = ':';
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final Set<DigestAuthorizationToken> MANDATORY_REQUEST_TOKENS;

    static {
        Set<DigestAuthorizationToken> mandatoryTokens = new HashSet<DigestAuthorizationToken>();
        mandatoryTokens.add(DigestAuthorizationToken.USERNAME);
        mandatoryTokens.add(DigestAuthorizationToken.REALM);
        mandatoryTokens.add(DigestAuthorizationToken.NONCE);
        mandatoryTokens.add(DigestAuthorizationToken.DIGEST_URI);
        mandatoryTokens.add(DigestAuthorizationToken.RESPONSE);

        MANDATORY_REQUEST_TOKENS = Collections.unmodifiableSet(mandatoryTokens);
    }

    /**
     * The {@link List} of supported algorithms, this is assumed to be in priority order.
     */
    private final List<DigestAlgorithm> supportedAlgorithms;
    private final List<DigestQop> supportedQops;
    private final String qopString;
    private final String realmName; // TODO - Will offer choice once backing store API/SPI is in.
    private final byte[] realmBytes;
    private final CallbackHandler callbackHandler;
    private final NonceManager nonceManager;

    // Where do session keys fit? Do we just hang onto a session key or keep visiting the user store to check if the password
    // has changed?
    // Maybe even support registration of a session so it can be invalidated?

    public DigestAuthenticationMechanism(final List<DigestAlgorithm> supportedAlgorithms, final List<DigestQop> supportedQops,
            final String realmName, final CallbackHandler callbackHandler, final NonceManager nonceManager) {
        this.supportedAlgorithms = supportedAlgorithms;
        this.supportedQops = supportedQops;
        this.realmName = realmName;
        this.realmBytes = realmName.getBytes(UTF_8);
        this.callbackHandler = callbackHandler;
        this.nonceManager = nonceManager;

        if (supportedQops.size() > 0) {
            StringBuilder sb = new StringBuilder();
            Iterator<DigestQop> it = supportedQops.iterator();
            sb.append(it.next().getToken());
            while (it.hasNext()) {
                sb.append(",").append(it.next().getToken());
            }
            qopString = sb.toString();
        } else {
            qopString = null;
        }
    }

    public IoFuture<AuthenticationResult> authenticate(HttpServerExchange exchange) {
        ConcreteIoFuture<AuthenticationResult> result = new ConcreteIoFuture<AuthenticationResult>();
        Deque<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.startsWith(DIGEST_PREFIX)) {
                    String digestChallenge = current.substring(PREFIX_LENGTH);

                    try {
                        DigestContext context = new DigestContext();
                        Map<DigestAuthorizationToken, String> parsedHeader = parseHeader(digestChallenge);
                        context.setParsedHeader(parsedHeader);
                        // Some form of Digest authentication is going to occur so get the DigestContext set on the exchange.
                        exchange.putAttachment(DigestContext.ATTACHMENT_KEY, context);

                        dispatch(exchange, new DigestRunnable(result, exchange));

                        // The request has now potentially been dispatched to a different worker thread, the run method
                        // within BasicRunnable is now responsible for ensuring the request continues.
                        return result;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // By this point we had a header we should have been able to verify but for some reason
                // it was not correctly structured.
                // By this point we had a header we should have been able to verify but for some reason
                // it was not correctly structured.
                result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                return result;
            }
        }

        // No suitable header has been found in this request,
        result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_ATTEMPTED, null));
        return result;
    }

    @Override
    public void handleComplete(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        if (Util.shouldChallenge(exchange)) {
            dispatch(exchange, new SendChallengeRunnable(exchange, completionHandler));
        } else {
            dispatch(exchange, new SendAuthenticationInfoHeader(exchange, completionHandler));
        }
    }

    private final class DigestRunnable implements Runnable {

        private final ConcreteIoFuture<AuthenticationResult> result;
        private final HttpServerExchange exchange;
        private final DigestContext context;
        private final Map<DigestAuthorizationToken, String> parsedHeader;
        private MessageDigest digest;

        private DigestRunnable(final ConcreteIoFuture<AuthenticationResult> result, HttpServerExchange exchange) {
            this.result = result;
            this.exchange = exchange;
            context = exchange.getAttachment(DigestContext.ATTACHMENT_KEY);
            this.parsedHeader = context.getParsedHeader();
        }

        public void run() {
            // Step 1 - Verify the set of tokens received to ensure valid values.
            Set<DigestAuthorizationToken> mandatoryTokens = new HashSet<DigestAuthorizationToken>(MANDATORY_REQUEST_TOKENS);
            if (supportedAlgorithms.contains(DigestAlgorithm.MD5) == false) {
                // If we don't support MD5 then the client must choose an algorithm as we can not fall back to MD5.
                mandatoryTokens.add(DigestAuthorizationToken.ALGORITHM);
            }
            if (supportedQops.isEmpty() == false && supportedQops.contains(DigestQop.AUTH) == false) {
                // If we do not support auth then we are mandating auth-int so force the client to send a QOP
                mandatoryTokens.add(DigestAuthorizationToken.MESSAGE_QOP);
            }

            DigestQop qop = null;
            // This check is early as is increases the list of mandatory tokens.
            if (parsedHeader.containsKey(DigestAuthorizationToken.MESSAGE_QOP)) {
                qop = DigestQop.forName(parsedHeader.get(DigestAuthorizationToken.MESSAGE_QOP));
                if (qop == null || supportedQops.contains(qop) == false) {
                    // We are also ensuring the client is not trying to force a qop that has been disabled.
                    REQUEST_LOGGER.invalidTokenReceived(DigestAuthorizationToken.MESSAGE_QOP.getName(),
                            parsedHeader.get(DigestAuthorizationToken.MESSAGE_QOP));
                    // TODO - This actually needs to result in a HTTP 400 Bad Request response and not a new challenge.
                    result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                    return;
                }
                context.setQop(qop);
                mandatoryTokens.add(DigestAuthorizationToken.CNONCE);
                mandatoryTokens.add(DigestAuthorizationToken.NONCE_COUNT);
            }

            // Check all mandatory tokens are present.
            mandatoryTokens.removeAll(parsedHeader.keySet());
            if (mandatoryTokens.size() > 0) {
                for (DigestAuthorizationToken currentToken : mandatoryTokens) {
                    // TODO - Need a better check and possible concatenate the list of tokens - however
                    // even having one missing token is not something we should routinely expect.
                    REQUEST_LOGGER.missingAuthorizationToken(currentToken.getName());
                }
                // TODO - This actually needs to result in a HTTP 400 Bad Request response and not a new challenge.
                result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                return;
            }

            // Perform some validation of the remaining tokens.
            if (realmName.equals(parsedHeader.get(DigestAuthorizationToken.REALM)) == false) {
                REQUEST_LOGGER.invalidTokenReceived(DigestAuthorizationToken.REALM.getName(),
                        parsedHeader.get(DigestAuthorizationToken.REALM));
                // TODO - This actually needs to result in a HTTP 400 Bad Request response and not a new challenge.
                result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                return;
            }

            // TODO - Validate the URI

            if (parsedHeader.containsKey(DigestAuthorizationToken.OPAQUE)) {
                if (OPAQUE_VALUE.equals(parsedHeader.get(DigestAuthorizationToken.OPAQUE)) == false) {
                    REQUEST_LOGGER.invalidTokenReceived(DigestAuthorizationToken.OPAQUE.getName(),
                            parsedHeader.get(DigestAuthorizationToken.OPAQUE));
                    result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                    return;
                }
            }

            DigestAlgorithm algorithm;
            if (parsedHeader.containsKey(DigestAuthorizationToken.ALGORITHM)) {
                algorithm = DigestAlgorithm.forName(parsedHeader.get(DigestAuthorizationToken.ALGORITHM));
                if (algorithm == null || supportedAlgorithms.contains(algorithm) == false) {
                    // We are also ensuring the client is not trying to force an algorithm that has been disabled.
                    REQUEST_LOGGER.invalidTokenReceived(DigestAuthorizationToken.ALGORITHM.getName(),
                            parsedHeader.get(DigestAuthorizationToken.ALGORITHM));
                    // TODO - This actually needs to result in a HTTP 400 Bad Request response and not a new challenge.
                    result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                    return;
                }
            } else {
                // We know this is safe as the algorithm token was made mandatory
                // if MD5 is not supported.
                algorithm = DigestAlgorithm.MD5;
            }

            // Step 2 - Based on the headers received verify that in theory the response is valid.
            try {
                digest = algorithm.getMessageDigest();
                context.setDigest(digest);
            } catch (NoSuchAlgorithmException e) {
                // This is really not expected but the API makes us consider it.
                REQUEST_LOGGER.exceptionProcessingRequest(e);
                result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                return;
            }

            byte[] ha1;
            // Step 2.1 Calculate H(A1)
            try {
                if (algorithm.isSession()) {
                    ha1 = lookupOrCreateSessionHA1(parsedHeader);
                } else {
                    // This is the most simple form of a hash involving the username, realm and password.
                    ha1 = createHA1();
                }
                context.setHa1(ha1);
            } catch (AuthenticationException e) {
                // Most likely the user does not exist.
                result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                return;
            }

            byte[] ha2;
            // Step 2.2 Calculate H(A2)
            if (qop == null || qop.equals(DigestQop.AUTH)) {
                ha2 = createHA2Auth();
            } else {
                ha2 = createHA2AuthInt();
            }

            byte[] requestDigest;
            if (qop == null) {
                requestDigest = createRFC2069RequestDigest(ha1, ha2);
            } else {
                requestDigest = createRFC2617RequestDigest(ha1, ha2);
            }

            byte[] providedResponse = parsedHeader.get(DigestAuthorizationToken.RESPONSE).getBytes(UTF_8);
            if (MessageDigest.isEqual(requestDigest, providedResponse) == false) {
                // TODO - We should look at still marking the nonce as used, a failure in authentication due to say a failure
                // looking up the users password would leave it open to the packet being replayed.
                REQUEST_LOGGER.authenticationFailed(parsedHeader.get(DigestAuthorizationToken.USERNAME), DIGEST.toString());
                result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                return;
            }

            // Step 3 - Verify that the nonce was eligible to be used.
            if (validateNonceUse() == false) {
                // TODO - This is the right place to make use of the decision but the check needs to be much much sooner
                // otherwise a failure server
                // side could leave a packet that could be 're-played' after the failed auth.
                // The username and password verification passed but for some reason we do not like the nonce.
                context.markStale();
                result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                return;
            }

            // We have authenticated the remote user.
            final String userName = parsedHeader.get(DigestAuthorizationToken.USERNAME);
            Principal principal = (new Principal() {

                @Override
                public String getName() {
                    return userName;
                }
            });
            result.setResult(new AuthenticationResult(principal, AuthenticationOutcome.AUTHENTICATED, Collections.<String>emptySet()));

            // Step 4 - Set up any QOP related requirements.

            // TODO - Do QOP
        }

        private boolean validateNonceUse() {
            String suppliedNonce = parsedHeader.get(DigestAuthorizationToken.NONCE);
            int nonceCount = -1;
            if (parsedHeader.containsKey(DigestAuthorizationToken.NONCE_COUNT)) {
                String nonceCountHex = parsedHeader.get(DigestAuthorizationToken.NONCE_COUNT);

                nonceCount = Integer.parseInt(nonceCountHex, 16);
            }

            context.setNonce(suppliedNonce);
            // TODO - A replay attempt will need an exception.
            return (nonceManager.validateNonce(suppliedNonce, nonceCount, exchange));
        }

        private byte[] getExpectedPassword() throws AuthenticationException {
            NameCallback ncb = new NameCallback("Username", parsedHeader.get(DigestAuthorizationToken.USERNAME));
            PasswordCallback pcp = new PasswordCallback("Password", false);

            try {
                callbackHandler.handle(new Callback[] { ncb, pcp });
            } catch (IOException e) {
                throw new AuthenticationException(e);
            } catch (UnsupportedCallbackException e) {
                throw new AuthenticationException(e);
            }

            return new String(pcp.getPassword()).getBytes(UTF_8);
        }

        private byte[] createHA1() throws AuthenticationException {
            byte[] userName = parsedHeader.get(DigestAuthorizationToken.USERNAME).getBytes(UTF_8);
            byte[] password = getExpectedPassword();

            try {
                digest.update(userName);
                digest.update(COLON);
                digest.update(realmBytes);
                digest.update(COLON);
                digest.update(password);

                return HexConverter.convertToHexBytes(digest.digest());
            } finally {
                digest.reset();
            }
        }

        private byte[] lookupOrCreateSessionHA1(final Map<DigestAuthorizationToken, String> parsedHeader) {
            // TODO - Implement method.
            throw new IllegalStateException("Method not implemented.");
        }

        private byte[] createHA2Auth() {
            byte[] method = exchange.getRequestMethod().toString().getBytes(UTF_8);
            byte[] digestUri = parsedHeader.get(DigestAuthorizationToken.DIGEST_URI).getBytes(UTF_8);

            try {
                digest.update(method);
                digest.update(COLON);
                digest.update(digestUri);

                return HexConverter.convertToHexBytes(digest.digest());
            } finally {
                digest.reset();
            }
        }

        private byte[] createHA2AuthInt() {
            // TODO - Implement method.
            throw new IllegalStateException("Method not implemented.");
        }

        private byte[] createRFC2069RequestDigest(final byte[] ha1, final byte[] ha2) {
            byte[] nonce = parsedHeader.get(DigestAuthorizationToken.NONCE).getBytes(UTF_8);

            try {
                digest.update(ha1);
                digest.update(COLON);
                digest.update(nonce);
                digest.update(COLON);
                digest.update(ha2);

                return HexConverter.convertToHexBytes(digest.digest());
            } finally {
                digest.reset();
            }
        }

        private byte[] createRFC2617RequestDigest(final byte[] ha1, final byte[] ha2) {
            byte[] nonce = parsedHeader.get(DigestAuthorizationToken.NONCE).getBytes(UTF_8);
            byte[] nonceCount = parsedHeader.get(DigestAuthorizationToken.NONCE_COUNT).getBytes(UTF_8);
            byte[] cnonce = parsedHeader.get(DigestAuthorizationToken.CNONCE).getBytes(UTF_8);
            byte[] qop = parsedHeader.get(DigestAuthorizationToken.MESSAGE_QOP).getBytes(UTF_8);

            try {
                digest.update(ha1);
                digest.update(COLON);
                digest.update(nonce);
                digest.update(COLON);
                digest.update(nonceCount);
                digest.update(COLON);
                digest.update(cnonce);
                digest.update(COLON);
                digest.update(qop);
                digest.update(COLON);
                digest.update(ha2);

                return HexConverter.convertToHexBytes(digest.digest());
            } finally {
                digest.reset();
            }
        }

    }

    private class SendChallengeRunnable implements Runnable {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler next;

        private SendChallengeRunnable(final HttpServerExchange exchange, final HttpCompletionHandler next) {
            this.exchange = exchange;
            this.next = next;
        }

        public void run() {
            DigestContext context = exchange.getAttachment(DigestContext.ATTACHMENT_KEY);
            boolean stale = context == null ? false : context.isStale();

            StringBuilder rb = new StringBuilder(DIGEST_PREFIX);
            rb.append(Headers.REALM.toString()).append("=\"").append(realmName).append("\",");
            rb.append(Headers.DOMAIN.toString()).append("=\"/\","); // TODO - This will need to be generated
                                                                    // based on security constraints.
            rb.append(Headers.NONCE.toString()).append("=\"").append(nonceManager.nextNonce(null, exchange)).append("\",");
            // Not currently using OPAQUE as it offers no integrity, used for session data leaves it vulnerable to
            // session fixation type issues as well.
            rb.append(Headers.OPAQUE.toString()).append("=\"00000000000000000000000000000000\"");
            if (stale) {
                rb.append(",stale=true");
            }
            if (supportedAlgorithms.size() > 0) {
                // This header will need to be repeated once for each algorithm.
                rb.append(",").append(Headers.ALGORITHM.toString()).append("=%s");
            }
            if (qopString != null) {
                rb.append(",").append(Headers.QOP.toString()).append("=\"").append(qopString).append("\"");
            }

            String theChallenge = rb.toString();
            HeaderMap responseHeader = exchange.getResponseHeaders();
            if (supportedAlgorithms.size() > 0) {
                for (DigestAlgorithm current : supportedAlgorithms) {
                    responseHeader.add(WWW_AUTHENTICATE, String.format(theChallenge, current.getToken()));
                }
            } else {
                responseHeader.add(WWW_AUTHENTICATE, theChallenge);
            }
            exchange.setResponseCode(CODE_401.getCode());

            next.handleComplete();
        }
    }

    private class SendAuthenticationInfoHeader implements Runnable {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler next;
        private final DigestContext context;

        private SendAuthenticationInfoHeader(final HttpServerExchange exchange, final HttpCompletionHandler next) {
            this.exchange = exchange;
            context = exchange.getAttachment(DigestContext.ATTACHMENT_KEY);
            this.next = next;
        }

        public void run() {
            DigestQop qop = context.getQop();
            String currentNonce = context.getNonce();
            String nextNonce = nonceManager.nextNonce(currentNonce, exchange);
            if (qop != null || nextNonce.equals(currentNonce) == false) {
                StringBuilder sb = new StringBuilder();
                sb.append(NEXT_NONCE).append("=\"").append(nextNonce).append("\"");
                if (qop != null) {
                    Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();
                    sb.append(",").append(Headers.QOP.toString()).append("=\"").append(qop.getToken()).append("\"");
                    byte[] ha1 = context.getHa1();
                    byte[] ha2;

                    if (qop == DigestQop.AUTH) {
                        ha2 = createHA2Auth();
                    } else {
                        ha2 = createHA2AuthInt();
                    }
                    String rspauth = createRFC2617RequestDigest(ha1, ha2);
                    sb.append(",").append(Headers.RESPONSE_AUTH.toString()).append("=\"").append(rspauth).append("\"");
                    sb.append(",").append(Headers.CNONCE.toString()).append("=\"").append(parsedHeader.get(DigestAuthorizationToken.CNONCE)).append("\"");
                    sb.append(",").append(Headers.NONCE_COUNT.toString()).append("=").append(parsedHeader.get(DigestAuthorizationToken.NONCE_COUNT));
                }

                HeaderMap responseHeader = exchange.getResponseHeaders();
                responseHeader.add(AUTHENTICATION_INFO, sb.toString());
            }

            exchange.removeAttachment(DigestContext.ATTACHMENT_KEY);
            next.handleComplete();
        }

        private byte[] createHA2Auth() {
            byte[] digestUri = context.getParsedHeader().get(DigestAuthorizationToken.DIGEST_URI).getBytes(UTF_8);

            MessageDigest digest = context.getDigest();
            try {
                digest.update(COLON);
                digest.update(digestUri);

                return HexConverter.convertToHexBytes(digest.digest());
            } finally {
                digest.reset();
            }
        }

        private byte[] createHA2AuthInt() {
            // TODO - Implement method.
            throw new IllegalStateException("Method not implemented.");
        }

        // TODO - Get all digesting into a single wrapper of the MessageDigest.
        private String createRFC2617RequestDigest(final byte[] ha1, final byte[] ha2) {
            Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();
            byte[] nonce = parsedHeader.get(DigestAuthorizationToken.NONCE).getBytes(UTF_8);
            byte[] nonceCount = parsedHeader.get(DigestAuthorizationToken.NONCE_COUNT).getBytes(UTF_8);
            byte[] cnonce = parsedHeader.get(DigestAuthorizationToken.CNONCE).getBytes(UTF_8);
            byte[] qop = parsedHeader.get(DigestAuthorizationToken.MESSAGE_QOP).getBytes(UTF_8);
            MessageDigest digest = context.getDigest();

            try {
                digest.update(ha1);
                digest.update(COLON);
                digest.update(nonce);
                digest.update(COLON);
                digest.update(nonceCount);
                digest.update(COLON);
                digest.update(cnonce);
                digest.update(COLON);
                digest.update(qop);
                digest.update(COLON);
                digest.update(ha2);

                return HexConverter.convertToHexString(digest.digest());
            } finally {
                digest.reset();
            }
        }

    }

    private static class DigestContext {

        static AttachmentKey<DigestContext> ATTACHMENT_KEY = AttachmentKey.create(DigestContext.class);

        private String nonce;
        private DigestQop qop;
        private byte[] ha1;
        private MessageDigest digest;
        private boolean stale = false;
        Map<DigestAuthorizationToken, String> parsedHeader;

        public boolean isStale() {
            return stale;
        }

        public void markStale() {
            this.stale = true;
        }

        public String getNonce() {
            return nonce;
        }

        public void setNonce(String nonce) {
            this.nonce = nonce;
        }

        DigestQop getQop() {
            return qop;
        }

        void setQop(DigestQop qop) {
            this.qop = qop;
        }

        byte[] getHa1() {
            return ha1;
        }

        void setHa1(byte[] ha1) {
            this.ha1 = ha1;
        }

        MessageDigest getDigest() {
            return digest;
        }

        void setDigest(MessageDigest digest) {
            this.digest = digest;
        }

        Map<DigestAuthorizationToken, String> getParsedHeader() {
            return parsedHeader;
        }

        void setParsedHeader(Map<DigestAuthorizationToken, String> parsedHeader) {
            this.parsedHeader = parsedHeader;
        }

    }

    private class AuthenticationException extends Exception {

        private static final long serialVersionUID = 4123187263595319747L;

        // TODO - Remove unused constrcutors and maybe even move exception to higher level.

        public AuthenticationException() {
            super();
        }

        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }

        public AuthenticationException(String message) {
            super(message);
        }

        public AuthenticationException(Throwable cause) {
            super(cause);
        }

    }

}
