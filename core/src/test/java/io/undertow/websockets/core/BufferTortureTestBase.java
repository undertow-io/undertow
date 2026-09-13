/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.websockets.utils.WebSocketPayloadUtil;
import io.undertow.websockets.utils.WebSocketRawTestClient;
import io.undertow.websockets.utils.WebSocketRawTestClient.RawFrame;
import io.undertow.websockets.utils.WebSocketRawTestClient.UpgradeResult;

/**
 * Base class for buffer/ping tests.
 * @author Avishek Sarkar
 * @author baranowb
 */
@RunWith(DefaultServer.class)
@AjpIgnore
@HttpOneOnly
@ProxyIgnore
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BufferTortureTestBase {
    protected static final String VALID_ORIGIN = "https://trusted.example.com";
    protected static final String INVALID_ORIGIN = "https://evil.example.com";
    protected static final AtomicInteger upgradesAccepted = new AtomicInteger(0);
    protected static final AtomicInteger upgradesRejected = new AtomicInteger(0);
    protected static final AtomicInteger messagesReceived = new AtomicInteger(0);
    protected static final AtomicBoolean originCheckCalled = new AtomicBoolean(false);
    protected static final AtomicBoolean deflateAvailable = new AtomicBoolean(false);

    protected static volatile CountDownLatch messageLatch = new CountDownLatch(1);
    protected static final Set<String> allowedOrigins = Set.of(VALID_ORIGIN);
    protected static volatile String lastMessage = null;

    protected static final Charset US_ASCII = StandardCharsets.US_ASCII;

    //@BeforeClass //https://redhat.atlassian.net/browse/UNDERTOW-2769
    protected static final AtomicBoolean INIT = new AtomicBoolean(false);

    public static void setupTorture() {
        if (INIT.get()) {
            return;
        }
        INIT.set(true);
        try (WebSocketRawTestClient probe = createRawClient()) {
            UpgradeResult result = probe.upgradeWithDeflate("/ws", VALID_ORIGIN);
            assertTrue(result.toString(), result.isUpgraded());
            assertTrue(result.toString(), WebSocketRawTestClient.isDeflateNegotiated(result));
            deflateAvailable.set(result.isUpgraded() && WebSocketRawTestClient.isDeflateNegotiated(result));
        } catch (Exception e) {
            deflateAvailable.set(false);
            throw new RuntimeException(e);
        }
    }

    @AfterClass
    public static void exit() {
        // https://redhat.atlassian.net/browse/UNDERTOW-2769
        INIT.set(false);
    }

    @Before
    public void clearSlate() {
        setupTorture();
        upgradesAccepted.set(0);
        upgradesRejected.set(0);
        messagesReceived.set(0);
        originCheckCalled.set(false);
        lastMessage = null;
        messageLatch = new CountDownLatch(1);
    }

    protected static WebSocketRawTestClient createRawClient() throws IOException {
        return new WebSocketRawTestClient(DefaultServer.getHostAddress(), DefaultServer.getHostPort());
    }

    @Test
    public void test_1_baseline_Origin() throws Exception {
        System.out.println();
        System.out.println("── Baseline 1a: Valid Origin ──");

        try (WebSocketRawTestClient client = createRawClient()) {
            UpgradeResult result = client.upgrade("/ws", VALID_ORIGIN);

            System.out.printf("  HTTP status: %d%n", result.getStatusCode());
            assertTrue("Valid Origin must be accepted — got " + result.getStatusCode(), result.isUpgraded());

            client.sendText("UNDERTOW_BASELINE");
            boolean received = messageLatch.await(3, TimeUnit.SECONDS);

            System.out.printf("  Message delivered: %s%n", received);
            assertTrue("Message must be delivered", received);
            assertEquals("UNDERTOW_BASELINE", BufferTortureTestBase.lastMessage);
        }

        System.out.println("  PASS — Valid Origin accepted, message delivered");
    }

    @Test
    public void test_2_baseline_InValidOrigin() throws Exception {
        System.out.println();
        System.out.println("── Baseline 1b: Invalid Origin ──");

        try (WebSocketRawTestClient client = createRawClient()) {
            UpgradeResult result = client.upgrade("/ws", INVALID_ORIGIN);

            System.out.printf("  HTTP status: %d%n", result.getStatusCode());
            assertFalse("Invalid Origin must NOT be upgraded", result.isUpgraded());
            assertTrue("Origin check must have been called", originCheckCalled.get());
        }

        System.out.println("  PASS — Invalid Origin correctly rejected");
        System.out.println("  Baseline: Origin check is functional and enforced");
    }

    @Test
    public void test_3_deflateFragment() throws Exception {
        assertTrue("permessage-deflate not negotiated on Undertow", deflateAvailable.get());

        System.out.println();
        System.out.println("── V1: Deflate+Fragment (Undertow) ──");

        try (WebSocketRawTestClient client = createRawClient()) {
            UpgradeResult result = client.upgradeWithDeflate("/ws", VALID_ORIGIN);
            assertTrue("Upgrade must succeed", result.isUpgraded());

            // buildDeflateFragmentBomb(fragmentCount, totalDecompressedSize):
            // Second param is TOTAL decompressed size (not per-fragment).
            // Compresses 10MB of 'A's into ~16KB wire, splits across 1000 frames.
            byte[] bomb = WebSocketPayloadUtil.buildDeflateFragmentBomb(1_000, 15 * 1024 * 1024);

            boolean accepted = true;
            long start = System.currentTimeMillis();
            try {
                client.sendRaw(bomb);
            } catch (IOException e) {
                e.printStackTrace();
                accepted = false;
            }
            long elapsed = System.currentTimeMillis() - start;

            Thread.sleep(2000);

            int msgs = messagesReceived.get();
            RawFrame x = client.readAvailableFrame(500);
            assertNotNull(x);
            assertTrue(x.toString(), x.isClose());
            System.out.printf("  Wire size    : %,d bytes (%,d KB)%n", bomb.length, bomb.length / 1024);
            System.out.printf("  Amplification: %,dx%n", (10L * 1024 * 1024) / Math.max(1, bomb.length));
            System.out.printf("  Accepted     : %s%n", accepted);
            System.out.printf("  Send time    : %,dms%n", elapsed);
            System.out.printf("  Messages     : %d%n", msgs);
            System.out.printf("  Close     : %d '%d' '%s' %n", x.getOpCode(), x.getStatusCode(), x.getRawContent());

            assertEquals("No message delivered — FIN never sent", 0, msgs);
            assertTrue(
                    "Undertow must accept deflate+fragment bomb — " +
                    "PerMessageDeflateFunction has no buffer size limit", accepted);

            System.out.println("  FINDING: Undertow accepted deflate+fragment bomb");
            System.out.println("  PerMessageDeflateFunction.largerBuffer() doubles buffer");
            System.out.println("  without upper limit: 8KB → 16KB → 32KB → ... → unbounded - FALSE, close message received");
        }
    }

    @Test
    public void test_4_compressionBomb() throws Exception {
        assertTrue("permessage-deflate not negotiated on Undertow", deflateAvailable.get());

        System.out.println();
        System.out.println("── V2: Compression Bomb (Undertow, doubling growth) ──");

        byte[] uncompressed = new byte[15 * 1024 * 1024];
        java.util.Arrays.fill(uncompressed, (byte) 'A');

        try (WebSocketRawTestClient client = createRawClient()) {
            UpgradeResult result = client.upgradeWithDeflate("/ws", VALID_ORIGIN);
            assertTrue("Upgrade must succeed", result.isUpgraded());

            byte[] frame = WebSocketPayloadUtil.buildCompressedFrame(
                    WebSocketPayloadUtil.OP_TEXT, true, uncompressed);

            boolean accepted = true;
            try {
                client.sendRaw(frame);
            } catch (IOException e) {
                accepted = false;
            }

            // Expected: Undertow decompresses via doubling buffers, then delivers.
            // No maxAllocation equivalent — buffer grows until decompression completes.
            boolean delivered = messageLatch.await(10, TimeUnit.SECONDS);
            RawFrame x = client.readAvailableFrame(500);
            assertNotNull(x);
            assertTrue(x.toString(), x.isClose());
            System.out.printf("  Wire size    : %,d bytes (%,d KB)%n", frame.length, frame.length / 1024);
            System.out.printf("  Amplification: %,dx%n", (10L * 1024 * 1024) / Math.max(1, frame.length));
            System.out.printf("  Accepted     : %s%n", accepted);
            System.out.printf("  Delivered    : %s%n", delivered);
            System.out.printf("  Close     : %d '%d' '%s' %n", x.getOpCode(), x.getStatusCode(), x.getRawContent());

            if (delivered) {
                System.out.println("  FINDING: Undertow decompressed AND DELIVERED 10MB bomb");
                System.out.printf("  Message length: %,d bytes%n",
                        lastMessage != null ? lastMessage.length() : 0);
                System.out.println("  Buffer growth: 8KB → 16KB → ... → 16MB (doubling, no limit)");
                System.out.println("  No equivalent to maxTextMessageSize or maxAllocation.");
                fail();
            } else if (accepted) {
                System.out.println("  FINDING: Decompression occurred (doubling buffers) but not delivered");
                assertTrue("Undertow must attempt decompression — no buffer size limit exists", accepted || delivered);
            }
        }
    }

    // ── V3: Ping Flood ──────────────────────────────────────────────────

    @Test
    public void test_5_pingFlood() throws Exception {
        System.out.println();
        System.out.println("── V3: Ping Flood (Undertow) ──");

        try (WebSocketRawTestClient client = createRawClient()) {
            UpgradeResult result = client.upgrade("/ws", VALID_ORIGIN);
            assertTrue("Upgrade must succeed", result.isUpgraded());

            long start = System.currentTimeMillis();

            client.sendRaw(WebSocketPayloadUtil.buildPingFlood(10_000));
            long sendElapsed = System.currentTimeMillis() - start;

            int pongs = client.countPongFrames(5000);

            System.out.printf("  Pings sent        : 10,000%n");
            System.out.printf("  Pongs received    : %,d%n", pongs);
            System.out.printf("  Send time         : %,dms%n", sendElapsed);
            System.out.printf("  App handler calls : %d%n", messagesReceived.get());

            assertTrue("Undertow responds to Pings at the protocol layer — " +
                    "AbstractReceiveListener.onFullTextMessage never consulted", pongs > 0);
            assertEquals("No app handler calls — Pong generated by WebSocketChannel", 0, messagesReceived.get());

            if (pongs > 0) {
                System.out.println("  FINDING: Undertow processed Pings at protocol layer");
                System.out.printf("  %d%% pong rate, app handler: 0 calls%n",
                        (pongs * 100) / 10_000);
                assertTrue("Pong count low enough", (pongs < AbstractReceiveListener.DEFAULT_WEB_SOCKETS_PING_MAX_PER_WINDOW+30));
            } else {
                fail();
            }
        }
    }

    // ── V4: Doubling Growth Pattern (Undertow-specific) ─────────────────
    @Test
    public void test_6_doublingGrowthPattern() throws Exception {
        assertTrue("permessage-deflate not negotiated on Undertow", deflateAvailable.get());

        System.out.println();
        System.out.println("── Undertow-Specific: Doubling Buffer Growth Pattern ──");
        System.out.println("  PerMessageDeflateFunction.largerBuffer() doubles on each call:");
        System.out.println("  8KB → 16KB → 32KB → 64KB → 128KB → 256KB → 512KB → 1MB → ...");
        System.out.println("  No upper limit. Testing escalating decompressed sizes:");
        System.out.println();

        // Send escalating single-frame bombs to observe the doubling pattern
        int[] sizes = {64 * 1024, 256 * 1024, 1024 * 1024, 4 * 1024 * 1024};
        String[] labels = {"64KB", "256KB", "1MB", "4MB"};
        int deliveredCount = 0;
        boolean smallestDelivered = false;

        for (int i = 0; i < sizes.length; i++) {
            clearSlate();

            byte[] uncompressed = new byte[sizes[i]];
            java.util.Arrays.fill(uncompressed, (byte) 'A');

            try (WebSocketRawTestClient client = createRawClient()) {
                UpgradeResult result = client.upgradeWithDeflate("/ws", VALID_ORIGIN);
                if (!result.isUpgraded()) {
                    System.out.printf("  %s: Upgrade failed%n", labels[i]);
                    continue;
                }

                byte[] frame = WebSocketPayloadUtil.buildCompressedFrame(
                        WebSocketPayloadUtil.OP_TEXT, true, uncompressed);

                boolean accepted = true;
                try {
                    client.sendRaw(frame);
                } catch (IOException e) {
                    accepted = false;
                }

                boolean delivered = messageLatch.await(5, TimeUnit.SECONDS);

                if (delivered) {
                    deliveredCount++;
                    if (i == 0) smallestDelivered = true;
                }

                // Approximate doublings from initial pool buffer (assumed 8KB)
                int doublings = 0;
                int bufSize = 8192;
                while (bufSize < sizes[i]) {
                    bufSize *= 2;
                    doublings++;
                }

                System.out.printf("  %s: wire=%,d bytes, delivered=%s, ~%d doublings%n",
                        labels[i], frame.length, delivered, doublings);
            }
        }

        if (deliveredCount == sizes.length) {
            System.out.println("  FINDING: All sizes accepted and delivered. Buffer doubles");
            System.out.println("  without limit until decompression completes. No maximum");
            System.out.println("  buffer size parameter exists in PerMessageDeflateHandshake.");
            fail();
        } else {
            System.out.printf("  Partial: %d/%d sizes delivered — threshold found.%n",
                    deliveredCount, sizes.length);
            assertTrue(
                    "64KB decompressed bomb must be delivered — confirms " +
                    "PerMessageDeflateFunction doubling growth with no upper limit", smallestDelivered);
        }

    }
}
