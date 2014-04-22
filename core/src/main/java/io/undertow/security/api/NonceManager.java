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
package io.undertow.security.api;

import io.undertow.server.HttpServerExchange;

/**
 * A NonceManager is used by the HTTP Digest authentication mechanism to request nonces and to validate the nonces sent from the
 * client.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface NonceManager {

    // TODO - Should a nonce manager be able to tie these to a connection or session, or any other piece of info we have about
    // the client?
    // Also different rules depending on HTTP method or the resource being accessed?

    /**
     * Select the next nonce to be sent from the server taking into account the last valid nonce.
     *
     * It is both possible and likely that the nonce last used by the client will still be valid, in that case the same nonce
     * will be returned.
     *
     * @param lastNonce - The last valid nonce received from the client or null if we don't already have a nonce.
     * @return The next nonce to be sent in a challenge to the client.
     */
    String nextNonce(final String lastNonce, final HttpServerExchange exchange);

    /**
     * Validate that a nonce can be used.
     *
     * If the nonce can not be used but the related digest was correct then a new nonce should be returned to the client
     * indicating that the nonce was stale.
     *
     * For implementations of this interface this method is not expected by be idempotent, i.e. once a nonce is validated with a
     * specific nonceCount it is not expected that this method will return true again if the same combination is presented.
     *
     * This method is expected to ONLY be called if the users credentials are valid as a storage overhead could be incurred
     * this overhead must not be accessible to unauthenticated clients.
     *
     * @param nonce - The nonce received from the client.
     * @param nonceCount - The nonce count from the client or -1 of none specified.
     * @return true if the nonce can be used otherwise return false.
     */
    boolean validateNonce(final String nonce, final int nonceCount, final HttpServerExchange exchange);

}
