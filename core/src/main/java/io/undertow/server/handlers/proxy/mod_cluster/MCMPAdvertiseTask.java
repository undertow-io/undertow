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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy.mod_cluster;

import io.undertow.UndertowLogger;
import io.undertow.util.NetworkUtils;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import org.xnio.channels.MulticastMessageChannel;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * @author Emanuel Muckenhuber
 */
class MCMPAdvertiseTask implements Runnable {

    public static final String RFC_822_FMT = "EEE, d MMM yyyy HH:mm:ss Z";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(RFC_822_FMT, Locale.US);
    private static final boolean linuxLike;
    private static final boolean windows;

    static {
        String value = System.getProperty("os.name");
        linuxLike = (value != null) && (value.toLowerCase().startsWith("linux") || value.toLowerCase().startsWith("mac") || value.toLowerCase().startsWith("hp"));
        windows = (value != null) && value.toLowerCase().contains("win");
    }

    private volatile int seq = 0;

    private final String protocol;
    private final String host;
    private final int port;
    private final String path;
    private final byte[] ssalt;
    private final MessageDigest md;
    private final InetSocketAddress address;
    private final ModClusterContainer container;
    private final MulticastMessageChannel channel;

    static void advertise(final ModClusterContainer container, final MCMPConfig.AdvertiseConfig config, final XnioWorker worker) throws IOException {
        InetSocketAddress bindAddress;
        final InetAddress group = InetAddress.getByName(config.getAdvertiseAddress());
        if (group == null) {
            bindAddress = new InetSocketAddress(config.getAdvertisePort());
        } else {
            bindAddress = new InetSocketAddress(group, config.getAdvertisePort());
        }
        MulticastMessageChannel channel;
        try {
            channel = worker.createUdpServer(bindAddress, null, OptionMap.EMPTY);
        } catch (IOException e) {
            if(group != null && (linuxLike || windows)) {
                //try again with no group
                //see UNDERTOW-454
                UndertowLogger.ROOT_LOGGER.potentialCrossTalking(group, (group instanceof Inet4Address) ? "IPv4" : "IPv6", e.getLocalizedMessage());
                bindAddress = new InetSocketAddress(config.getAdvertisePort());
                channel = worker.createUdpServer(bindAddress, null, OptionMap.EMPTY);
            } else {
                throw e;
            }
        }
        final MCMPAdvertiseTask task = new MCMPAdvertiseTask(container, config, channel);
        //execute immediately, so there is no delay before load balancing starts working
        channel.getIoThread().execute(task);
        channel.getIoThread().executeAtInterval(task, config.getAdvertiseFrequency(), TimeUnit.MILLISECONDS);
    }

    MCMPAdvertiseTask(final ModClusterContainer container, final MCMPConfig.AdvertiseConfig config, final MulticastMessageChannel channel) throws IOException {

        this.container = container;
        this.protocol = config.getProtocol();
        // MODCLUSTER-483 mod_cluster client does not yet support ipv6 addresses with zone indices so skip it
        String host = config.getManagementSocketAddress().getHostString();
        int zoneIndex = host.indexOf("%");
        this.host = (zoneIndex < 0) ? host : host.substring(0, zoneIndex);
        this.port = config.getManagementSocketAddress().getPort();
        this.path = config.getPath();
        this.channel = channel;

        final InetAddress group = InetAddress.getByName(config.getAdvertiseGroup());
        address = new InetSocketAddress(group, config.getAdvertisePort());
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        final String securityKey = config.getSecurityKey();
        if (securityKey == null) {
            // Security key is not configured, so the result hash was zero bytes
            ssalt = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        } else {
            md.reset();
            digestString(md, securityKey);
            ssalt = md.digest();
        }

        UndertowLogger.ROOT_LOGGER.proxyAdvertisementsStarted(address.toString(), config.getAdvertiseFrequency());
    }

    private static final String CRLF = "\r\n";

    /*
     * the messages to send are something like:
     *
     * HTTP/1.0 200 OK
     * Date: Thu, 13 Sep 2012 09:24:02 GMT
     * Sequence: 5
     * Digest: ae8e7feb7cd85be346134657de3b0661
     * Server: b58743ba-fd84-11e1-bd12-ad866be2b4cc
     * X-Manager-Address: 127.0.0.1:6666
     * X-Manager-Url: /b58743ba-fd84-11e1-bd12-ad866be2b4cc
     * X-Manager-Protocol: http
     * X-Manager-Host: 10.33.144.3
     *
     */
    @Override
    public void run() {
        try {
            /*
             * apr_uuid_get(&magd->suuid);
             * magd->srvid[0] = '/';
             * apr_uuid_format(&magd->srvid[1], &magd->suuid);
             * In fact we use the srvid on the 2 second byte [1]
             */
            final byte[] ssalt = this.ssalt;
            final String server = container.getServerID();
            final String date = DATE_FORMAT.format(new Date(System.currentTimeMillis()));
            final String seq = "" + this.seq++;

            final byte[] digest;
            synchronized (md) {
                md.reset();
                md.update(ssalt);
                digestString(md, date);
                digestString(md, seq);
                digestString(md, server);
                digest = md.digest();
            }
            final String digestString = bytesToHexString(digest);

            final StringBuilder builder = new StringBuilder();
            builder.append("HTTP/1.0 200 OK").append(CRLF)
                    .append("Date: ").append(date).append(CRLF)
                    .append("Sequence: ").append(seq).append(CRLF)
                    .append("Digest: ").append(digestString).append(CRLF)
                    .append("Server: ").append(server).append(CRLF)
                    .append("X-Manager-Address: ").append(NetworkUtils.formatPossibleIpv6Address(host)).append(":").append(port).append(CRLF)
                    .append("X-Manager-Url: ").append(path).append(CRLF)
                    .append("X-Manager-Protocol: ").append(protocol).append(CRLF)
                    .append("X-Manager-Host: ").append(host).append(CRLF);

            final String payload = builder.toString();
            final ByteBuffer byteBuffer = ByteBuffer.wrap(payload.getBytes(StandardCharsets.US_ASCII));
            UndertowLogger.ROOT_LOGGER.proxyAdvertiseMessagePayload(payload);
            channel.sendTo(address, byteBuffer);
        } catch (Exception e) {
            UndertowLogger.ROOT_LOGGER.proxyAdvertiseCannotSendMessage(e, address);
        }
    }

    private void digestString(MessageDigest md, String securityKey) {
        byte[] buf = securityKey.getBytes(StandardCharsets.UTF_8);
        md.update(buf);
    }

    private static final char[] TABLE = "0123456789abcdef".toCharArray();
    static String bytesToHexString(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(TABLE[b >> 4 & 0x0f]).append(TABLE[b & 0x0f]);
        }
        return builder.toString();
    }

}
