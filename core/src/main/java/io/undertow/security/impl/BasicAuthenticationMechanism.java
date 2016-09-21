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

import static io.undertow.UndertowMessages.MESSAGES;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;

/**
 * The authentication handler responsible for BASIC authentication as described by RFC2617
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class BasicAuthenticationMechanism implements AuthenticationMechanism {

    public static final String SILENT = "silent";
    public static final String CHARSET = "charset";
    /**
     * A comma separated list of patterns and charsets. The pattern is a regular expression.
     *
     * Because different browsers user different encodings this allows for the correct encoding to be selected based
     * on the current browser. In general though it is recommended that BASIC auth not be used when passwords contain
     * characters outside ASCII, as some browsers use the current locate to determine encoding.
     *
     * This list must have an even number of elements, as it is interpreted as pattern,charset,pattern,charset,...
     */
    public static final String USER_AGENT_CHARSETS = "user-agent-charsets";

    private final String name;
    private final String challenge;

    private static final String BASIC_PREFIX = BASIC + " ";
    private static final String LOWERCASE_BASIC_PREFIX = BASIC_PREFIX.toLowerCase(Locale.ENGLISH);
    private static final int PREFIX_LENGTH = BASIC_PREFIX.length();
    private static final String COLON = ":";

    /**
     * If silent is true then this mechanism will only take effect if there is an Authorization header.
     *
     * This allows you to combine basic auth with form auth, so human users will use form based auth, but allows
     * programmatic clients to login using basic auth.
     */
    private final boolean silent;

    private final IdentityManager identityManager;

    private final Charset charset;
    private final Map<Pattern, Charset> userAgentCharsets;

    public BasicAuthenticationMechanism(final String realmName) {
        this(realmName, "BASIC");
    }

    public BasicAuthenticationMechanism(final String realmName, final String mechanismName) {
        this(realmName, mechanismName, false);
    }

    public BasicAuthenticationMechanism(final String realmName, final String mechanismName, final boolean silent) {
        this(realmName, mechanismName, silent, null);
    }
    public BasicAuthenticationMechanism(final String realmName, final String mechanismName, final boolean silent, final IdentityManager identityManager) {
        this(realmName, mechanismName, silent, identityManager, StandardCharsets.UTF_8, Collections.emptyMap());
    }

    public BasicAuthenticationMechanism(final String realmName, final String mechanismName, final boolean silent, final IdentityManager identityManager, Charset charset, Map<Pattern, Charset> userAgentCharsets) {
        this.challenge = BASIC_PREFIX + "realm=\"" + realmName + "\"";
        this.name = mechanismName;
        this.silent = silent;
        this.identityManager = identityManager;
        this.charset = charset;
        this.userAgentCharsets = Collections.unmodifiableMap(new LinkedHashMap<>(userAgentCharsets));
    }

    @SuppressWarnings("deprecation")
    private IdentityManager getIdentityManager(SecurityContext securityContext) {
        return identityManager != null ? identityManager : securityContext.getIdentityManager();
    }

    /**
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {

        List<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.toLowerCase(Locale.ENGLISH).startsWith(LOWERCASE_BASIC_PREFIX)) {

                    String base64Challenge = current.substring(PREFIX_LENGTH);
                    String plainChallenge = null;
                    try {
                        ByteBuffer decode = FlexBase64.decode(base64Challenge);

                        Charset charset = this.charset;
                        if(!userAgentCharsets.isEmpty()) {
                            String ua = exchange.getRequestHeaders().getFirst(Headers.USER_AGENT);
                            if(ua != null) {
                                for (Map.Entry<Pattern, Charset> entry : userAgentCharsets.entrySet()) {
                                    if(entry.getKey().matcher(ua).find()) {
                                        charset = entry.getValue();
                                        break;
                                    }
                                }
                            }
                        }

                        plainChallenge = new String(decode.array(), decode.arrayOffset(), decode.limit(), charset);
                        UndertowLogger.SECURITY_LOGGER.debugf("Found basic auth header %s (decoded using charset %s) in %s", plainChallenge, charset, exchange);
                    } catch (IOException e) {
                        UndertowLogger.SECURITY_LOGGER.debugf(e, "Failed to decode basic auth header %s in %s", base64Challenge, exchange);
                    }
                    int colonPos;
                    if (plainChallenge != null && (colonPos = plainChallenge.indexOf(COLON)) > -1) {
                        String userName = plainChallenge.substring(0, colonPos);
                        char[] password = plainChallenge.substring(colonPos + 1).toCharArray();

                        IdentityManager idm = getIdentityManager(securityContext);
                        PasswordCredential credential = new PasswordCredential(password);
                        try {
                            final AuthenticationMechanismOutcome result;
                            Account account = idm.verify(userName, credential);
                            if (account != null) {
                                securityContext.authenticationComplete(account, name, false);
                                result = AuthenticationMechanismOutcome.AUTHENTICATED;
                            } else {
                                securityContext.authenticationFailed(MESSAGES.authenticationFailed(userName), name);
                                result = AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                            }
                            return result;
                        } finally {
                            clear(password);
                        }
                    }

                    // By this point we had a header we should have been able to verify but for some reason
                    // it was not correctly structured.
                    return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }
            }
        }

        // No suitable header has been found in this request,
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        if(silent) {
            //if this is silent we only send a challenge if the request contained auth headers
            //otherwise we assume another method will send the challenge
            String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION);
            if(authHeader == null) {
                return new ChallengeResult(false);
            }
        }
        exchange.getResponseHeaders().add(WWW_AUTHENTICATE, challenge);
        UndertowLogger.SECURITY_LOGGER.debugf("Sending basic auth challenge %s for %s", challenge, exchange);
        return new ChallengeResult(true, UNAUTHORIZED);
    }

    private static void clear(final char[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = 0x00;
        }
    }

    public static class Factory implements AuthenticationMechanismFactory {

        private final IdentityManager identityManager;

        public Factory(IdentityManager identityManager) {
            this.identityManager = identityManager;
        }

        @Override
        public AuthenticationMechanism create(String mechanismName, FormParserFactory formParserFactory, Map<String, String> properties) {
            String realm = properties.get(REALM);
            String silent = properties.get(SILENT);
            String charsetString = properties.get(CHARSET);
            Charset charset = charsetString == null ? StandardCharsets.UTF_8 : Charset.forName(charsetString);
            Map<Pattern, Charset> userAgentCharsets = new HashMap<>();
            String userAgentString = properties.get(USER_AGENT_CHARSETS);
            if(userAgentString != null) {
                String[] parts = userAgentString.split(",");
                if(parts.length % 2 != 0) {
                    throw UndertowMessages.MESSAGES.userAgentCharsetMustHaveEvenNumberOfItems(userAgentString);
                }
                for(int i = 0; i < parts.length; i += 2) {
                    Pattern pattern = Pattern.compile(parts[i]);
                    Charset c = Charset.forName(parts[i + 1]);
                    userAgentCharsets.put(pattern, c);
                }
            }

            return new BasicAuthenticationMechanism(realm, mechanismName, silent != null && silent.equals("true"), identityManager, charset, userAgentCharsets);
        }
    }

}
