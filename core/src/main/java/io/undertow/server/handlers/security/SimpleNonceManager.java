/*
 * JBoss, Home of Professional Open Source.
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

import static io.undertow.UndertowMessages.MESSAGES;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

/**
 * A default {@link NonceManager} implementation to provide reasonable single host management of nonces.
 *
 * This {@link NonceManager} manages nonces in two groups, the first is the group that are allocated to new requests, this group
 * is a problem as we want to be able to limit how many we distribute so we don't have a DOS storing too many but we also don't
 * a high number of requests to to push the other valid nonces out faster than they can be used.
 *
 * The second group is the set of nonces actively in use - these should be maintained as we can also maintain the nonce count
 * and even track the next nonce once invalid.
 *
 * Maybe group one should be a timestamp and private key hashed together, if used with a nonce count they move to be tracked to
 * ensure the same count is not used again - if successfully used without a nonce count add to a blacklist until expiration? A
 * nonce used without a nonce count will essentially be single use with each request getting a new nonce.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SimpleNonceManager implements NonceManager {

    private static final String DEFAULT_HASH_ALG = "MD5";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final String secret;
    private final String hashAlg;

    /**
     * After a nonce is issued the first authentication response MUST be received within 5 minutes.
     */
    private final long firstUseTimeOut = 5 * 60 * 1000;

    /**
     * Overall a nonce is valid from 15 minutes from first being issued, if used after this then a new nonce will be issued.
     */
    private final long overallTimeOut = 15 * 60 * 1000;

    /**
     * This is the time before the expiration of the current nonce that a replacement nonce will be sent to the client
     * pro-actively, i.e. from 5 minutes before the expiration of the nonce the client will be asked to use the next nonce.
     */
    private final long newNonceOverlap = 5 * 60 * 1000;

    public SimpleNonceManager() {
        this(DEFAULT_HASH_ALG);
    }

    public SimpleNonceManager(final String hashAlg) {
        // Verify it is a valid algorithm (at least for now)
        MessageDigest digest = getDigest(hashAlg);

        this.hashAlg = hashAlg;

        // Create a new secret only valid within this NonceManager instance.
        Random rand = new SecureRandom();
        byte[] secretBytes = new byte[32];
        rand.nextBytes(secretBytes);
        secret = Base64.encodeBytes(digest.digest(secretBytes));
    }

    private MessageDigest getDigest(final String hashAlg) {
        try {
            return MessageDigest.getInstance(hashAlg);
        } catch (NoSuchAlgorithmException e) {
            throw MESSAGES.hashAlgorithmNotFound(hashAlg);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see io.undertow.server.handlers.security.NonceManager#nextNonce(java.lang.String)
     */
    @Override
    public String nextNonce(String lastNonce) {
        if (lastNonce == null) {
            return createNewNonce();
        }

        return lastNonce;
    }

    private String createNewNonce() {
        String now = Long.toString(System.currentTimeMillis());

        MessageDigest digest = getDigest(hashAlg);
        digest.update(now.getBytes());
        byte[] hashedPart = digest.digest(secret.getBytes(UTF_8));
        byte[] timeStampPart = now.getBytes(UTF_8);
        byte[] complete = new byte[1 + timeStampPart.length + hashedPart.length];
        complete[0] = (byte) timeStampPart.length;
        System.arraycopy(timeStampPart, 0, complete, 1, timeStampPart.length);
        System.arraycopy(hashedPart, 0, complete, 1 + timeStampPart.length, hashedPart.length);

        return Base64.encodeBytes(complete);
    }

    // TODO - Should the nonce manager also be responsible for storing the session key or do we also need a session manager?
    //        Maybe actually a session manager, once a replacement nonce is issued we could still update the session manager.

    /**
     *
     *
     * @see io.undertow.server.handlers.security.NonceManager#validateNonce(java.lang.String, int)
     */
    @Override
    public boolean validateNonce(String nonce, int nonceCount) {
        // We only need to perform the verification involving the secret if this is a nonce we
        // don't already know about - if we do know about it then it must have passed through this
        // verification once already.
        byte[] complete;
        try {
            complete = Base64.decode(nonce.getBytes(UTF_8));
        } catch (IOException e) {
            throw MESSAGES.invalidBase64Token(e);
        }
        MessageDigest digest = getDigest(hashAlg);
        int timeStampLength = complete[0];
        int predictedDigestLength = digest.getDigestLength();
        // A sanity check to try and verify the sizes we expect from the arrays are correct.
        if (predictedDigestLength > 0) {
            int expectedLength = 1 + timeStampLength + predictedDigestLength;
            if (complete.length != expectedLength) {
                throw MESSAGES.invalidNonceReceived();
            } else if (timeStampLength + 1 >= complete.length)
                throw MESSAGES.invalidNonceReceived();
        }

        byte[] timeStampBytes = new byte[timeStampLength];
        byte[] providedHashedPart = new byte[complete.length - 1 - timeStampBytes.length];
        System.arraycopy(complete, timeStampBytes.length + 1, providedHashedPart, 0, providedHashedPart.length);
        System.arraycopy(complete, 1, timeStampBytes, 0, timeStampBytes.length);

        digest.update(timeStampBytes);
        byte[] calculatedHashedPart = digest.digest(secret.getBytes(UTF_8));

        //
        System.out.println("Extracted Time Stampe " + new String(timeStampBytes, UTF_8));
        //

        return MessageDigest.isEqual(providedHashedPart, calculatedHashedPart);

        // Should this also be tied to a user? i.e. a different user can not use someone elses nonce or is the count enough to
        // pick up abuse?
    }

    /**
     * Class to hold information about the use of a nonce.
     *
     */
    private class NonceRecord {
        /**
         * The nonce this record relates to.
         */
        private String nonce;
        private long timestamp;

        /**
         * The next nonce issued to replace this nonce.
         */
        private String nextNonce;

    }

    public static void main(String[] args) throws Exception {
        NonceManager nm = new SimpleNonceManager();
        String nonce = nm.nextNonce(null);
        System.out.println("Nonce = " + nonce);
        System.out.println("Is Valid = " + nm.validateNonce(nonce, -1));
        System.out.println("Is Valid = " + nm.validateNonce(nonce + "AAAAAA", -1));
    }

}
