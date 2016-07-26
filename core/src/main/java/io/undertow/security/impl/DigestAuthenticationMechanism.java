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

import static io.undertow.UndertowLogger.REQUEST_LOGGER;
import static io.undertow.UndertowMessages.MESSAGES;
import static io.undertow.security.impl.DigestAuthorizationToken.parseHeader;
import static io.undertow.util.Headers.AUTHENTICATION_INFO;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.DIGEST;
import static io.undertow.util.Headers.NEXT_NONCE;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;

import io.undertow.UndertowLogger;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.NonceManager;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.DigestAlgorithm;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HexConverter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link io.undertow.server.HttpHandler} to handle HTTP Digest authentication, both according to RFC-2617 and draft update to allow additional
 * algorithms to be used.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DigestAuthenticationMechanism implements AuthenticationMechanism {

    private static final String DEFAULT_NAME = "DIGEST";
    private static final String DIGEST_PREFIX = DIGEST + " ";
    private static final int PREFIX_LENGTH = DIGEST_PREFIX.length();
    private static final String OPAQUE_VALUE = "00000000000000000000000000000000";
    private static final byte COLON = ':';

    private final String mechanismName;
    private final IdentityManager identityManager;

    private static final Set<DigestAuthorizationToken> MANDATORY_REQUEST_TOKENS;

    static {
        Set<DigestAuthorizationToken> mandatoryTokens = new HashSet<>();
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
    private final String domain;
    private final NonceManager nonceManager;

    // Where do session keys fit? Do we just hang onto a session key or keep visiting the user store to check if the password
    // has changed?
    // Maybe even support registration of a session so it can be invalidated?
    // 2013-05-29 - Session keys will be cached, where a cached key is used the IdentityManager is still given the
    //              opportunity to check the Account is still valid.

    public DigestAuthenticationMechanism(final List<DigestAlgorithm> supportedAlgorithms, final List<DigestQop> supportedQops,
            final String realmName, final String domain, final NonceManager nonceManager) {
        this(supportedAlgorithms, supportedQops, realmName, domain, nonceManager, DEFAULT_NAME);
    }

    public DigestAuthenticationMechanism(final List<DigestAlgorithm> supportedAlgorithms, final List<DigestQop> supportedQops,
            final String realmName, final String domain, final NonceManager nonceManager, final String mechanismName) {
        this(supportedAlgorithms, supportedQops, realmName, domain, nonceManager, mechanismName, null);
    }

    public DigestAuthenticationMechanism(final List<DigestAlgorithm> supportedAlgorithms, final List<DigestQop> supportedQops,
            final String realmName, final String domain, final NonceManager nonceManager, final String mechanismName, final IdentityManager identityManager) {
        this.supportedAlgorithms = supportedAlgorithms;
        this.supportedQops = supportedQops;
        this.realmName = realmName;
        this.domain = domain;
        this.nonceManager = nonceManager;
        this.mechanismName = mechanismName;
        this.identityManager = identityManager;

        if (!supportedQops.isEmpty()) {
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

    public DigestAuthenticationMechanism(final String realmName, final String domain, final String mechanismName) {
        this(realmName, domain, mechanismName, null);
    }

    public DigestAuthenticationMechanism(final String realmName, final String domain, final String mechanismName, final IdentityManager identityManager) {
        this(Collections.singletonList(DigestAlgorithm.MD5), Collections.singletonList(DigestQop.AUTH), realmName, domain, new SimpleNonceManager(), DEFAULT_NAME, identityManager);
    }

    @SuppressWarnings("deprecation")
    private IdentityManager getIdentityManager(SecurityContext securityContext) {
        return identityManager != null ? identityManager : securityContext.getIdentityManager();
    }

    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange,
                                                       final SecurityContext securityContext) {
        List<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.startsWith(DIGEST_PREFIX)) {
                    String digestChallenge = current.substring(PREFIX_LENGTH);

                    try {
                        DigestContext context = new DigestContext();
                        Map<DigestAuthorizationToken, String> parsedHeader = parseHeader(digestChallenge);
                        context.setMethod(exchange.getRequestMethod().toString());
                        context.setParsedHeader(parsedHeader);
                        // Some form of Digest authentication is going to occur so get the DigestContext set on the exchange.
                        exchange.putAttachment(DigestContext.ATTACHMENT_KEY, context);

                        UndertowLogger.SECURITY_LOGGER.debugf("Found digest header %s in %s", current, exchange);

                        return handleDigestHeader(exchange, securityContext);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // By this point we had a header we should have been able to verify but for some reason
                // it was not correctly structured.
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
        }

        // No suitable header has been found in this request,
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    public AuthenticationMechanismOutcome handleDigestHeader(HttpServerExchange exchange, final SecurityContext securityContext) {
        DigestContext context = exchange.getAttachment(DigestContext.ATTACHMENT_KEY);
        Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();
        // Step 1 - Verify the set of tokens received to ensure valid values.
        Set<DigestAuthorizationToken> mandatoryTokens = new HashSet<>(MANDATORY_REQUEST_TOKENS);
        if (!supportedAlgorithms.contains(DigestAlgorithm.MD5)) {
            // If we don't support MD5 then the client must choose an algorithm as we can not fall back to MD5.
            mandatoryTokens.add(DigestAuthorizationToken.ALGORITHM);
        }
        if (!supportedQops.isEmpty() && !supportedQops.contains(DigestQop.AUTH)) {
            // If we do not support auth then we are mandating auth-int so force the client to send a QOP
            mandatoryTokens.add(DigestAuthorizationToken.MESSAGE_QOP);
        }

        DigestQop qop = null;
        // This check is early as is increases the list of mandatory tokens.
        if (parsedHeader.containsKey(DigestAuthorizationToken.MESSAGE_QOP)) {
            qop = DigestQop.forName(parsedHeader.get(DigestAuthorizationToken.MESSAGE_QOP));
            if (qop == null || !supportedQops.contains(qop)) {
                // We are also ensuring the client is not trying to force a qop that has been disabled.
                REQUEST_LOGGER.invalidTokenReceived(DigestAuthorizationToken.MESSAGE_QOP.getName(),
                        parsedHeader.get(DigestAuthorizationToken.MESSAGE_QOP));
                // TODO - This actually needs to result in a HTTP 400 Bad Request response and not a new challenge.
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
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
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        // Perform some validation of the remaining tokens.
        if (!realmName.equals(parsedHeader.get(DigestAuthorizationToken.REALM))) {
            REQUEST_LOGGER.invalidTokenReceived(DigestAuthorizationToken.REALM.getName(),
                    parsedHeader.get(DigestAuthorizationToken.REALM));
            // TODO - This actually needs to result in a HTTP 400 Bad Request response and not a new challenge.
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        // TODO - Validate the URI

        if (parsedHeader.containsKey(DigestAuthorizationToken.OPAQUE)) {
            if (!OPAQUE_VALUE.equals(parsedHeader.get(DigestAuthorizationToken.OPAQUE))) {
                REQUEST_LOGGER.invalidTokenReceived(DigestAuthorizationToken.OPAQUE.getName(),
                        parsedHeader.get(DigestAuthorizationToken.OPAQUE));
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
        }

        DigestAlgorithm algorithm;
        if (parsedHeader.containsKey(DigestAuthorizationToken.ALGORITHM)) {
            algorithm = DigestAlgorithm.forName(parsedHeader.get(DigestAuthorizationToken.ALGORITHM));
            if (algorithm == null || !supportedAlgorithms.contains(algorithm)) {
                // We are also ensuring the client is not trying to force an algorithm that has been disabled.
                REQUEST_LOGGER.invalidTokenReceived(DigestAuthorizationToken.ALGORITHM.getName(),
                        parsedHeader.get(DigestAuthorizationToken.ALGORITHM));
                // TODO - This actually needs to result in a HTTP 400 Bad Request response and not a new challenge.
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
        } else {
            // We know this is safe as the algorithm token was made mandatory
            // if MD5 is not supported.
            algorithm = DigestAlgorithm.MD5;
        }

        try {
            context.setAlgorithm(algorithm);
        } catch (NoSuchAlgorithmException e) {
            /*
             * This should not be possible in a properly configured installation.
             */
            REQUEST_LOGGER.exceptionProcessingRequest(e);
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        final String userName = parsedHeader.get(DigestAuthorizationToken.USERNAME);
        final IdentityManager identityManager = getIdentityManager(securityContext);
        final Account account;

        if (algorithm.isSession()) {
            /* This can follow one of the following: -
             *   1 - New session so use DigestCredentialImpl with the IdentityManager to
             *       create a new session key.
             *   2 - Obtain the existing session key from the session store and validate it, just use
             *       IdentityManager to validate account is still active and the current role assignment.
             */
            throw new IllegalStateException("Not yet implemented.");
        } else {
            final DigestCredential credential = new DigestCredentialImpl(context);
            account = identityManager.verify(userName, credential);
        }

        if (account == null) {
            // Authentication has failed, this could either be caused by the user not-existing or it
            // could be caused due to an invalid hash.
            securityContext.authenticationFailed(MESSAGES.authenticationFailed(userName), mechanismName);
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        // Step 3 - Verify that the nonce was eligible to be used.
        if (!validateNonceUse(context, parsedHeader, exchange)) {
            // TODO - This is the right place to make use of the decision but the check needs to be much much sooner
            // otherwise a failure server
            // side could leave a packet that could be 're-played' after the failed auth.
            // The username and password verification passed but for some reason we do not like the nonce.
            context.markStale();
            // We do not mark as a failure on the security context as this is not quite a failure, a client with a cached nonce
            // can easily hit this point.
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }

        // We have authenticated the remote user.

        sendAuthenticationInfoHeader(exchange);
        securityContext.authenticationComplete(account, mechanismName, false);
        return AuthenticationMechanismOutcome.AUTHENTICATED;

        // Step 4 - Set up any QOP related requirements.

        // TODO - Do QOP
    }

    private boolean validateRequest(final DigestContext context, final byte[] ha1) {
        byte[] ha2;
        DigestQop qop = context.getQop();
        // Step 2.2 Calculate H(A2)
        if (qop == null || qop.equals(DigestQop.AUTH)) {
            ha2 = createHA2Auth(context, context.getParsedHeader());
        } else {
            ha2 = createHA2AuthInt();
        }

        byte[] requestDigest;
        if (qop == null) {
            requestDigest = createRFC2069RequestDigest(ha1, ha2, context);
        } else {
            requestDigest = createRFC2617RequestDigest(ha1, ha2, context);
        }

        byte[] providedResponse = context.getParsedHeader().get(DigestAuthorizationToken.RESPONSE).getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(requestDigest, providedResponse);
    }

    private boolean validateNonceUse(DigestContext context, Map<DigestAuthorizationToken, String> parsedHeader, final HttpServerExchange exchange) {
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

    private byte[] createHA2Auth(final DigestContext context, Map<DigestAuthorizationToken, String> parsedHeader) {
        byte[] method = context.getMethod().getBytes(StandardCharsets.UTF_8);
        byte[] digestUri = parsedHeader.get(DigestAuthorizationToken.DIGEST_URI).getBytes(StandardCharsets.UTF_8);

        MessageDigest digest = context.getDigest();
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

    private byte[] createRFC2069RequestDigest(final byte[] ha1, final byte[] ha2, final DigestContext context) {
        final MessageDigest digest = context.getDigest();
        final Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();

        byte[] nonce = parsedHeader.get(DigestAuthorizationToken.NONCE).getBytes(StandardCharsets.UTF_8);

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

    private byte[] createRFC2617RequestDigest(final byte[] ha1, final byte[] ha2, final DigestContext context) {
        final MessageDigest digest = context.getDigest();
        final Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();

        byte[] nonce = parsedHeader.get(DigestAuthorizationToken.NONCE).getBytes(StandardCharsets.UTF_8);
        byte[] nonceCount = parsedHeader.get(DigestAuthorizationToken.NONCE_COUNT).getBytes(StandardCharsets.UTF_8);
        byte[] cnonce = parsedHeader.get(DigestAuthorizationToken.CNONCE).getBytes(StandardCharsets.UTF_8);
        byte[] qop = parsedHeader.get(DigestAuthorizationToken.MESSAGE_QOP).getBytes(StandardCharsets.UTF_8);

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

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        DigestContext context = exchange.getAttachment(DigestContext.ATTACHMENT_KEY);
        boolean stale = context == null ? false : context.isStale();

        StringBuilder rb = new StringBuilder(DIGEST_PREFIX);
        rb.append(Headers.REALM.toString()).append("=\"").append(realmName).append("\",");
        rb.append(Headers.DOMAIN.toString()).append("=\"").append(domain).append("\",");
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
        if (supportedAlgorithms.isEmpty()) {
            responseHeader.add(WWW_AUTHENTICATE, theChallenge);
        } else {
            for (DigestAlgorithm current : supportedAlgorithms) {
                responseHeader.add(WWW_AUTHENTICATE, String.format(theChallenge, current.getToken()));
            }
        }

        return new ChallengeResult(true, UNAUTHORIZED);
    }

    public void sendAuthenticationInfoHeader(final HttpServerExchange exchange) {
        DigestContext context = exchange.getAttachment(DigestContext.ATTACHMENT_KEY);
        DigestQop qop = context.getQop();
        String currentNonce = context.getNonce();
        String nextNonce = nonceManager.nextNonce(currentNonce, exchange);
        if (qop != null || !nextNonce.equals(currentNonce)) {
            StringBuilder sb = new StringBuilder();
            sb.append(NEXT_NONCE).append("=\"").append(nextNonce).append("\"");
            if (qop != null) {
                Map<DigestAuthorizationToken, String> parsedHeader = context.getParsedHeader();
                sb.append(",").append(Headers.QOP.toString()).append("=\"").append(qop.getToken()).append("\"");
                byte[] ha1 = context.getHa1();
                byte[] ha2;

                if (qop == DigestQop.AUTH) {
                    ha2 = createHA2Auth(context);
                } else {
                    ha2 = createHA2AuthInt();
                }
                String rspauth = new String(createRFC2617RequestDigest(ha1, ha2, context), StandardCharsets.UTF_8);
                sb.append(",").append(Headers.RESPONSE_AUTH.toString()).append("=\"").append(rspauth).append("\"");
                sb.append(",").append(Headers.CNONCE.toString()).append("=\"").append(parsedHeader.get(DigestAuthorizationToken.CNONCE)).append("\"");
                sb.append(",").append(Headers.NONCE_COUNT.toString()).append("=").append(parsedHeader.get(DigestAuthorizationToken.NONCE_COUNT));
            }

            HeaderMap responseHeader = exchange.getResponseHeaders();
            responseHeader.add(AUTHENTICATION_INFO, sb.toString());
        }

        exchange.removeAttachment(DigestContext.ATTACHMENT_KEY);
    }

    private byte[] createHA2Auth(final DigestContext context) {
        byte[] digestUri = context.getParsedHeader().get(DigestAuthorizationToken.DIGEST_URI).getBytes(StandardCharsets.UTF_8);

        MessageDigest digest = context.getDigest();
        try {
            digest.update(COLON);
            digest.update(digestUri);

            return HexConverter.convertToHexBytes(digest.digest());
        } finally {
            digest.reset();
        }
    }

    private static class DigestContext {

        static final AttachmentKey<DigestContext> ATTACHMENT_KEY = AttachmentKey.create(DigestContext.class);

        private String method;
        private String nonce;
        private DigestQop qop;
        private byte[] ha1;
        private DigestAlgorithm algorithm;
        private MessageDigest digest;
        private boolean stale = false;
        Map<DigestAuthorizationToken, String> parsedHeader;

        String getMethod() {
            return method;
        }

        void setMethod(String method) {
            this.method = method;
        }

        boolean isStale() {
            return stale;
        }

        void markStale() {
            this.stale = true;
        }

        String getNonce() {
            return nonce;
        }

        void setNonce(String nonce) {
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

        DigestAlgorithm getAlgorithm() {
            return algorithm;
        }

        void setAlgorithm(DigestAlgorithm algorithm) throws NoSuchAlgorithmException {
            this.algorithm = algorithm;
            digest = algorithm.getMessageDigest();
        }

        MessageDigest getDigest()  {
            return digest;
        }

        Map<DigestAuthorizationToken, String> getParsedHeader() {
            return parsedHeader;
        }

        void setParsedHeader(Map<DigestAuthorizationToken, String> parsedHeader) {
            this.parsedHeader = parsedHeader;
        }

    }

    private class DigestCredentialImpl implements DigestCredential {

        private final DigestContext context;

        private DigestCredentialImpl(final DigestContext digestContext) {
            this.context = digestContext;
        }

        @Override
        public DigestAlgorithm getAlgorithm() {
            return context.getAlgorithm();
        }

        @Override
        public boolean verifyHA1(byte[] ha1) {
            context.setHa1(ha1); // Cache for subsequent use.

            return validateRequest(context, ha1);
        }

        @Override
        public String getRealm() {
            return realmName;
        }

        @Override
        public byte[] getSessionData() {
            if (!context.getAlgorithm().isSession()) {
                throw MESSAGES.noSessionData();
            }

            byte[] nonce = context.getParsedHeader().get(DigestAuthorizationToken.NONCE).getBytes(StandardCharsets.UTF_8);
            byte[] cnonce = context.getParsedHeader().get(DigestAuthorizationToken.CNONCE).getBytes(StandardCharsets.UTF_8);

            byte[] response = new byte[nonce.length + cnonce.length + 1];
            System.arraycopy(nonce, 0, response, 0, nonce.length);
            response[nonce.length] = ':';
            System.arraycopy(cnonce, 0, response, nonce.length + 1, cnonce.length);

            return response;
        }

    }

    public static final class Factory implements AuthenticationMechanismFactory {

        private final IdentityManager identityManager;

        public Factory(IdentityManager identityManager) {
            this.identityManager = identityManager;
        }

        @Override
        public AuthenticationMechanism create(String mechanismName, FormParserFactory formParserFactory, Map<String, String> properties) {
            return new DigestAuthenticationMechanism(properties.get(REALM), properties.get(CONTEXT_PATH), mechanismName, identityManager);
        }
    }

}
