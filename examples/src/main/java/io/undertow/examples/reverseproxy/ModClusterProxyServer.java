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

package io.undertow.examples.reverseproxy;

import java.io.IOException;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.proxy.mod_cluster.MCMPConfig;
import io.undertow.server.handlers.proxy.mod_cluster.ModCluster;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author Jean-Frederic Clere
 */

@UndertowExample("ModCluster Proxy Server")
public class ModClusterProxyServer {

    /* the address and port to receive the MCMP elements */
    static String chost = System.getProperty("io.undertow.examples.proxy.CADDRESS", "localhost");
    static final int cport = Integer.parseInt(System.getProperty("io.undertow.examples.proxy.CPORT", "6666"));

    /* the address and port to receive normal requests */
    static String phost = System.getProperty("io.undertow.examples.proxy.ADDRESS", "localhost");
    static final int pport = Integer.parseInt(System.getProperty("io.undertow.examples.proxy.PORT", "8000"));

    public static void main(final String[] args) throws IOException {
        final XnioWorker worker = Xnio.getInstance().createWorker(OptionMap.EMPTY);
        final Undertow server;

        final ModCluster modCluster = ModCluster.builder(worker).build();
        try {
            if (chost == null) {
                // We are going to guess it.
                chost = java.net.InetAddress.getLocalHost().getHostName();
                System.out.println("Using: " + chost + ":" + cport);
            }

            modCluster.start();

            // Create the proxy and mgmt handler
            final HttpHandler proxy = modCluster.createProxyHandler();
            final MCMPConfig config = MCMPConfig.webBuilder()
                    .setManagementHost(chost)
                    .setManagementPort(cport)
                    .enableAdvertise()
                    .getParent()
                    .build();

            final HttpHandler mcmp = config.create(modCluster, proxy);

            server = Undertow.builder()
                    .addHttpListener(cport, chost)
                    .addHttpListener(pport, phost)
                    .setHandler(mcmp)
                    .build();
            server.start();

            // Start advertising the mcmp handler
            modCluster.advertise(config);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
