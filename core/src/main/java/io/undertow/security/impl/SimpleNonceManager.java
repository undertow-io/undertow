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

import io.undertow.security.api.SessionNonceManager;
import io.undertow.server.HttpServerExchange;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import org.xnio.XnioExecutor;
import org.xnio.XnioExecutor.Key;

import io.undertow.util.FlexBase64;

/**
 * A default {@link io.undertow.security.api.NonceManager} implementation to provide reasonable single host management of nonces.
 *
 * This {@link io.undertow.security.api.NonceManager} manages nonces in two groups, the first is the group that are allocated to new requests, this group
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
public class SimpleNonceManager implements SessionNonceManager {

    private static final String DEFAULT_HASH_ALG = "MD5";

    /**
     * List of invalid nonces, this list contains the nonces that have been used without a nonce count.
     *
     * In that situation they are considered single use and must not be used again.
     */
    private final Set<String> invalidNonces = Collections.synchronizedSet(new HashSet<String>());

    /**
     * Map of known currently valid nonces, a SortedMap is used to order the nonces by their creation time stamp allowing a
     * simple iteration over the keys to identify expired nonces.
     */
    private final Map<String, Nonce> knownNonces = Collections.synchronizedMap(new HashMap<String, Nonce>());

    /**
     * A WeakHashMap to map expired nonces to their replacement nonce. For an item to be added to this Collection the value will
     * have been removed from the knownNonces map.
     *
     * A replacement nonce will have been added to knownNonces that references the key used here - once the replacement nonce is
     * removed from knownNonces then the key will be eligible for garbage collection allowing it to be removed from this map as
     * well.
     *
     * The value in this Map is a plain String, this is to avoid inadvertently creating a long term reference to the key we
     * expect to be garbage collected at some point in the future.
     */
    private final Map<NonceHolder, String> forwardMapping = Collections.synchronizedMap(new WeakHashMap<NonceHolder, String>());

    /**
     * A pseudo-random generator for creating the nonces, a secure random is not required here as this is used purely to
     * minimise the chance of collisions should two nonces be generated at exactly the same time.
     */
    private final Random random = new Random();

    private final String secret;
    private final String hashAlg;
    private final int hashLength;

    /**
     * After a nonce is issued the first authentication response MUST be received within 5 minutes.
     */
    private static final long firstUseTimeOut = 5 * 60 * 1000;

    /**
     * Overall a nonce is valid from 15 minutes from first being issued, if used after this then a new nonce will be issued.
     */
    private static final long overallTimeOut = 15 * 60 * 1000;

    /**
     * A previously used nonce will be allowed to remain in the knownNonces list for up to 5 minutes.
     *
     * The nonce will be accepted during this 5 minute window but will immediately be replaced causing any additional requests
     * to be forced to use the new nonce.
     *
     * This is primarily for session based digests where loosing the cached session key would be bad.
     */
    private static final long cacheTimePostExpiry = 5 * 60 * 1000;

    public SimpleNonceManager() {
        this(DEFAULT_HASH_ALG);
    }

    public SimpleNonceManager(final String hashAlg) {
        // Verify it is a valid algorithm (at least for now)
        MessageDigest digest = getDigest(hashAlg);

        this.hashAlg = hashAlg;
        this.hashLength = digest.getDigestLength();

        // Create a new secret only valid within this NonceManager instance.
        Random rand = new SecureRandom();
        byte[] secretBytes = new byte[32];
        rand.nextBytes(secretBytes);
        secret = FlexBase64.encodeString(digest.digest(secretBytes), false);
    }

    private MessageDigest getDigest(final String hashAlg) {
        try {
            return MessageDigest.getInstance(hashAlg);
        } catch (NoSuchAlgorithmException e) {
            throw MESSAGES.hashAlgorithmNotFound(hashAlg);
        }
    }

    /**
     *
     * @see io.undertow.security.api.NonceManager#nextNonce(java.lang.String, io.undertow.server.HttpServerExchange)
     */
    public String nextNonce(String lastNonce, HttpServerExchange exchange) {
        if (lastNonce == null) {
            return createNewNonceString();
        }

        if (invalidNonces.contains(lastNonce)) {
            // The nonce supplied has already been used.
            return createNewNonceString();
        }

        String nonce = lastNonce;
        // Loop the forward mappings.
        synchronized (forwardMapping) {
            NonceHolder holder = new NonceHolder(lastNonce);
            while (forwardMapping.containsKey(holder)) {
                nonce = forwardMapping.get(holder);
                // The final NonceHolder will then be used if a forwardMapping needs to be set.
                holder = new NonceHolder(nonce);
            }

            synchronized (knownNonces) {
                Nonce value = knownNonces.get(nonce);
                if (value == null) {
                    // Not a likely scenario but if this occurs then most likely the nonce mapped to has also expired so we will
                    // just send a new nonce.
                    nonce = createNewNonceString();
                } else {
                    long now = System.currentTimeMillis();
                    // The cacheTimePostExpiry is not included here as this is our opportunity to inform the client to use a
                    // replacement nonce without a stale round trip.
                    long earliestAccepted = now - firstUseTimeOut;
                    if (value.timeStamp < earliestAccepted || value.timeStamp > now) {
                        XnioExecutor executor = exchange.getIoThread();
                        Nonce replacement = createNewNonce(holder);
                        if (value.executorKey != null) {
                            // The outcome doesn't matter - if we have the value we have all we need.
                            value.executorKey.remove();
                        }

                        nonce = replacement.nonce;
                        // Create a record of the forward mapping so if any requests do need to be marked stale they can be
                        // pointed towards the correct nonce to use.
                        forwardMapping.put(holder, nonce);
                        // Bring over any existing session key.
                        replacement.setSessionKey(value.getSessionKey());
                        // At this point we will not accept the nonce again so remove it from the list of known nonces but do
                        // register the replacement.
                        knownNonces.remove(holder.nonce);
                        // There are two reasons for registering the replacement 1 - to preserve any session key, 2 - To keep a
                        // reference to the now invalid key so it
                        // can be used as a key in a weak hash map.
                        knownNonces.put(nonce, replacement);
                        earliestAccepted = now - (overallTimeOut + cacheTimePostExpiry);
                        long timeTillExpiry = replacement.timeStamp - earliestAccepted;
                        replacement.executorKey = executor.executeAfter(new KnownNonceCleaner(nonce), timeTillExpiry,
                                TimeUnit.MILLISECONDS);

                    }
                }
            }
        }

        return nonce;
    }

    private String createNewNonceString() {
        return createNewNonce(null).nonce;
    }

    private Nonce createNewNonce(NonceHolder previousNonce) {
        byte[] prefix = new byte[8];
        random.nextBytes(prefix);
        long timeStamp = System.currentTimeMillis();
        byte[] now = Long.toString(timeStamp).getBytes(StandardCharsets.UTF_8);

        String nonce = createNonce(prefix, now);

        return new Nonce(nonce, timeStamp, previousNonce);
    }

    /**
     *
     * @see io.undertow.security.api.NonceManager#validateNonce(java.lang.String, int, io.undertow.server.HttpServerExchange)
     */
    @Override
    public boolean validateNonce(String nonce, int nonceCount, HttpServerExchange exchange) {
        XnioExecutor executor = exchange.getIoThread();
        if (nonceCount < 0) {
            if (invalidNonces.contains(nonce)) {
                // Without a nonce count the nonce is only usable once.
                return false;
            }
            // Not already known so will drop into first use validation.
        } else if (knownNonces.containsKey(nonce)) {
            // At this point we need to validate that the nonce is still within it's time limits,
            // If a new nonce had been selected then a known nonce would not have been found.
            // The nonce will also have it's nonce count checked.
            return validateNonceWithCount(new Nonce(nonce), nonceCount, executor);

        } else if (forwardMapping.containsKey(new NonceHolder(nonce))) {
            // We could have let this drop through as the next validation would fail anyway but
            // why waste the time if we already know a replacement nonce has been issued.
            return false;
        }

        // This is not a nonce currently known to us so start the validation process.
        Nonce value = verifyUnknownNonce(nonce, nonceCount);
        if (value == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        // NOTE - This check is for the first use, overall validity is checked in validateNonceWithCount.
        long earliestAccepted = now - firstUseTimeOut;
        if (value.timeStamp < earliestAccepted || value.timeStamp > now) {
            // The embedded timestamp is either expired or somehow is after now.
            return false;
        }

        if (nonceCount < 0) {
            // Allow a single use but reject all further uses.
            return addInvalidNonce(value, executor);
        } else {
            return validateNonceWithCount(value, nonceCount, executor);
        }
    }

    private boolean validateNonceWithCount(Nonce nonce, int nonceCount, final XnioExecutor executor) {
        // This point could have been reached either because the knownNonces map contained the key or because
        // it didn't and a count was supplied - either way need to double check the contents of knownNonces once
        // the lock is in place.
        synchronized (knownNonces) {
            Nonce value = knownNonces.get(nonce.nonce);
            long now = System.currentTimeMillis();
            // For the purpose of this validation we also add the cacheTimePostExpiry - when nextNonce is subsequently
            // called it will decide if we are in the interval to replace the nonce.
            long earliestAccepted = now - (overallTimeOut + cacheTimePostExpiry);
            if (value == null) {
                if (nonce.timeStamp < 0) {
                    // Means it was in there, now it isn't - most likely a timestamp expiration mid check - abandon validation.
                    return false;
                }

                if (nonce.timeStamp > earliestAccepted && nonce.timeStamp <= now) {
                    knownNonces.put(nonce.nonce, nonce);
                    long timeTillExpiry = nonce.timeStamp - earliestAccepted;
                    nonce.executorKey = executor.executeAfter(new KnownNonceCleaner(nonce.nonce), timeTillExpiry,
                            TimeUnit.MILLISECONDS);
                    return true;
                }

                return false;
            } else {
                // We have it, just need to verify that it has not expired and that the nonce key is valid.
                if (value.timeStamp < earliestAccepted || value.timeStamp > now) {
                    // The embedded timestamp is either expired or somehow is after now!!
                    return false;
                }

                if (value.getMaxNonceCount() < nonceCount) {
                    value.setMaxNonceCount(nonceCount);
                    return true;
                }

                return false;
            }

        }

    }

    private boolean addInvalidNonce(final Nonce nonce, final XnioExecutor executor) {
        long now = System.currentTimeMillis();
        long invalidBefore = now - firstUseTimeOut;

        long timeTillInvalid = nonce.timeStamp - invalidBefore;
        if (timeTillInvalid > 0) {
            if (invalidNonces.add(nonce.nonce)) {
                executor.executeAfter(new InvalidNonceCleaner(nonce.nonce), timeTillInvalid, TimeUnit.MILLISECONDS);
                return true;
            } else {
                return false;
            }
        } else {
            // So close to expiring any record of this nonce being used could have been cleared so
            // don't take a chance and just say no.
            return false;
        }
    }

    /**
     * Verify a previously unknown nonce and return the {@link NonceKey} representation for the nonce.
     *
     * Later when a nonce is re-used we can match based on the String alone - the information embedded within the nonce will be
     * cached with it.
     *
     * This stage of the verification simply extracts the prefix and the embedded timestamp and recreates a new hashed and
     * Base64 nonce based on the local secret - if the newly generated nonce matches the supplied one we accept it was created
     * by this nonce manager.
     *
     * This verification does not validate that the timestamp is within a valid time period.
     *
     * @param nonce -
     * @return
     */
    private Nonce verifyUnknownNonce(final String nonce, final int nonceCount) {
        byte[] complete;
        int offset;
        int length;
        try {
            ByteBuffer decode = FlexBase64.decode(nonce);
            complete = decode.array();
            offset = decode.arrayOffset();
            length = decode.limit() - offset;
        } catch (IOException e) {
            throw MESSAGES.invalidBase64Token(e);
        }

        int timeStampLength = complete[offset + 8];
        // A sanity check to try and verify the sizes we expect from the arrays are correct.
        if (hashLength > 0) {
            int expectedLength = 9 + timeStampLength + hashLength;
            if (length != expectedLength) {
                throw MESSAGES.invalidNonceReceived();
            } else if (timeStampLength + 1 >= length)
                throw MESSAGES.invalidNonceReceived();
        }

        byte[] prefix = new byte[8];
        System.arraycopy(complete, offset, prefix, 0, 8);
        byte[] timeStampBytes = new byte[timeStampLength];
        System.arraycopy(complete, offset + 9, timeStampBytes, 0, timeStampBytes.length);

        String expectedNonce = createNonce(prefix, timeStampBytes);

        if (expectedNonce.equals(nonce)) {
            try {
                long timeStamp = Long.parseLong(new String(timeStampBytes, StandardCharsets.UTF_8));

                return new Nonce(expectedNonce, timeStamp, nonceCount);
            } catch (NumberFormatException dropped) {
            }
        }

        return null;
    }

    private String createNonce(final byte[] prefix, final byte[] timeStamp) {
        byte[] hashedPart = generateHash(prefix, timeStamp);
        byte[] complete = new byte[9 + timeStamp.length + hashedPart.length];
        System.arraycopy(prefix, 0, complete, 0, 8);
        complete[8] = (byte) timeStamp.length;
        System.arraycopy(timeStamp, 0, complete, 9, timeStamp.length);
        System.arraycopy(hashedPart, 0, complete, 9 + timeStamp.length, hashedPart.length);

        return FlexBase64.encodeString(complete, false);
    }

    private byte[] generateHash(final byte[] prefix, final byte[] timeStamp) {
        MessageDigest digest = getDigest(hashAlg);

        digest.update(prefix);
        digest.update(timeStamp);

        return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
    }

    public void associateHash(String nonce, byte[] hash) {
        // TODO Auto-generated method stub

    }

    public byte[] lookupHash(String nonce) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * A simple wrapper around a nonce to allow it to be used as a key in a weak map.
     */
    private static class NonceHolder {
        private final String nonce;

        private NonceHolder(final String nonce) {
            if (nonce == null) {
                throw new NullPointerException("nonce must not be null.");
            }
            this.nonce = nonce;
        }

        @Override
        public int hashCode() {
            return nonce.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof NonceHolder) ? nonce.equals(((NonceHolder) obj).nonce) : false;
        }
    }

    /**
     * The state associated with a nonce.
     *
     * A NonceKey for a preciously valid nonce is also referenced, this is so that a WeakHashMap can be used to maintain a
     * mapping from the original NonceKey to the new nonce value.
     */
    private static class Nonce {

        private final String nonce;

        private final long timeStamp;
        // TODO we will also add a mechanism to track the gaps as the only restriction is that a NC can only be used one.
        private int maxNonceCount;
        // We keep this as it is used in the weak hash map as a forward mapping as long as the nonce to map to is still alive.
        @SuppressWarnings("unused")
        private final NonceHolder previousNonce;
        private byte[] sessionKey;
        private Key executorKey;

        private Nonce(final String nonce) {
            this(nonce, -1, -1);
        }

        private Nonce(final String nonce, final long timeStamp) {
            this(nonce, timeStamp, -1);
        }

        private Nonce(final String nonce, final long timeStamp, final int initialNC) {
            this(nonce, timeStamp, initialNC, null);
        }

        private Nonce(final String nonce, final long timeStamp, final NonceHolder previousNonce) {
            this(nonce, timeStamp, -1, previousNonce);
        }

        private Nonce(final String nonce, final long timeStamp, final int initialNC, final NonceHolder previousNonce) {
            this.nonce = nonce;
            this.timeStamp = timeStamp;
            this.maxNonceCount = initialNC;
            this.previousNonce = previousNonce;
        }

        byte[] getSessionKey() {
            return sessionKey;
        }

        void setSessionKey(final byte[] sessionKey) {
            this.sessionKey = sessionKey;
        }

        int getMaxNonceCount() {
            return maxNonceCount;
        }

        void setMaxNonceCount(int maxNonceCount) {
            this.maxNonceCount = maxNonceCount;
        }

    }

    private class InvalidNonceCleaner implements Runnable {

        private final String nonce;

        private InvalidNonceCleaner(final String nonce) {
            if (nonce == null) {
                throw new NullPointerException("nonce must not be null.");
            }
            this.nonce = nonce;
        }

        public void run() {
            invalidNonces.remove(nonce);
        }

    }

    private class KnownNonceCleaner implements Runnable {
        private final String nonce;

        private KnownNonceCleaner(final String nonce) {
            if (nonce == null) {
                throw new NullPointerException("nonce must not be null.");
            }
            this.nonce = nonce;
        }

        public void run() {
            knownNonces.remove(nonce);
        }
    }

}
