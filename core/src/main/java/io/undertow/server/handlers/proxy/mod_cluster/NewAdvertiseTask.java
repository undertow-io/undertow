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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.MulticastMessageChannel;

/**
 * @author Emanuel Muckenhuber
 */
public class NewAdvertiseTask implements Runnable {

    private final MulticastMessageChannel channel;
    private final SocketAddress address;

    public NewAdvertiseTask(MulticastMessageChannel channel, SocketAddress address) {
        this.channel = channel;
        this.address = address;
    }

    public static void main(String... args) throws IOException {

        final String group = "224.0.1.105";
        final int port = 23364;

        final InetAddress inetAddress = InetAddress.getByName(group);
        final InetSocketAddress address = new InetSocketAddress(inetAddress, port);
        final NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);

        final OptionMap options = OptionMap.create(Options.MULTICAST, true);

        final Xnio xnio = Xnio.getInstance("nio");
        final XnioWorker worker = xnio.createWorker(OptionMap.EMPTY);

        final MulticastMessageChannel channel = worker.createUdpServer(new InetSocketAddress(0), options);
        channel.resumeWrites();

        final Runnable r = new NewAdvertiseTask(channel, address);
        channel.getIoThread().executeAfter(r, 1, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        channel.getWriteSetter().set(new ChannelListener<MulticastMessageChannel>() {
            @Override
            public void handleEvent(MulticastMessageChannel channel) {
                final ByteBuffer buffer = ByteBuffer.wrap("Hello!".getBytes());
                try {
                    System.out.println("sending");
                    if (channel.sendTo(address, buffer)) {
                        System.out.println("sending");
//                        channel.getWriteSetter().set(null);
//                        channel.getIoThread().executeAfter(NewAdvertiseTask.this, 1, TimeUnit.SECONDS);
                    } else {
                        System.out.println("failed");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
