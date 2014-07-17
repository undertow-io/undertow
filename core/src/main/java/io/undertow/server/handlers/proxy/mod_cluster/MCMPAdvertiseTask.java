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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Emanuel Muckenhuber
 */
class MCMPAdvertiseTask implements Runnable {

    public static final String RFC_822_FMT = "EEE, d MMM yyyy HH:mm:ss Z";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(RFC_822_FMT);
    private static final boolean linuxLike;

    static {
        String value = System.getProperty("os.name");
        linuxLike = (value != null) && (value.toLowerCase().startsWith("linux") || value.toLowerCase().startsWith("mac") || value.toLowerCase().startsWith("hp"));
    }

    private volatile int seq = 0;

    private final String protocol;
    private final String host;
    private final int port;
    private final String path;
    private final byte[] ssalt;
    private final MessageDigest md;
    private final MulticastSocket socket;
    private final InetSocketAddress address;
    private final ModClusterContainer container;

    MCMPAdvertiseTask(final ModClusterContainer container, final MCMPConfig.AdvertiseConfig config) {

        this.container = container;
        this.protocol = config.getProtocol();
        this.host = config.getManagementHost();
        this.port = config.getManagementPort();
        this.path = config.getPath();

        try {
            final InetAddress group = InetAddress.getByName(config.getAdvertiseGroup());
            if (group == null && linuxLike) {
                address = new InetSocketAddress(config.getAdvertisePort());
            } else {
                address = new InetSocketAddress(group, config.getAdvertisePort());
            }
            socket = new MulticastSocket(address); // TODO use XNIO multicast channel
            socket.setTimeToLive(29);
            socket.joinGroup(group);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
                    .append("X-Manager-Address: ").append(host).append(":").append(port).append(CRLF)
                    .append("X-Manager-Url: ").append(path).append(CRLF)
                    .append("X-Manager-Protocol: ").append(protocol).append(CRLF)
                    .append("X-Manager-Host: ").append(host).append(CRLF);

            final String payload = builder.toString();
            byte[] buf = payload.getBytes();
            final DatagramPacket data = new DatagramPacket(buf, buf.length, address);
            socket.send(data);
        } catch (Exception Ex) {
            Ex.printStackTrace();
        }
    }

    private void digestString(MessageDigest md, String securityKey) {
        byte[] buf = securityKey.getBytes();
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