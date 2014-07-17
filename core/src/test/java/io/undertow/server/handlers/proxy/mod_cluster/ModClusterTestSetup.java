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

import java.util.concurrent.TimeUnit;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ResponseCodeHandler;

/**
 * Server setup to the run the mod_cluster tests
 *
 * -ea -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dtest.level=DEBUG -Djava.net.preferIPv4Stack=true
 *
 * @author Emanuel Muckenhuber
 */
public class ModClusterTestSetup {

    /* the address and port to receive the MCMP elements */
    static String chost = System.getProperty("io.undertow.examples.proxy.CADDRESS", "localhost");
    static final int cport = Integer.parseInt(System.getProperty("io.undertow.examples.proxy.CPORT", "6666"));

    /* the address and port to receive normal requests */
    static String phost = System.getProperty("io.undertow.examples.proxy.ADDRESS", "localhost");
    static final int pport = Integer.parseInt(System.getProperty("io.undertow.examples.proxy.PORT", "8000"));

    public static void main(final String[] args) {
        final Undertow server;

        final ModCluster modCluster = ModCluster.builder()
                .setHealthCheckInterval(TimeUnit.SECONDS.toMillis(3))
                .setRemoveBrokenNodes(TimeUnit.SECONDS.toMillis(30))
                .build();
        try {
            if (chost == null) {
                // We are going to guess it.
                chost = java.net.InetAddress.getLocalHost().getHostName();
                System.out.println("Using: " + chost + ":" + cport);
            }

            modCluster.start();

            // Create the proxy and mgmt handler
            final HttpHandler proxy = modCluster.getProxyHandler();
            final MCMPConfig config = MCMPConfig.builder()
                    .setManagementHost(chost)
                    .setManagementPort(cport)
                    .enableAdvertise()
                    .setSecurityKey("secret")
                    .getParent()
                    .build();

            final MCMPConfig webConfig = MCMPConfig.webBuilder()
                    .setManagementHost(chost)
                    .setManagementPort(cport)
                    .build();

            final HttpHandler mcmp = config.create(modCluster, proxy);
            final HttpHandler web = webConfig.create(modCluster, ResponseCodeHandler.HANDLE_404);

            server = Undertow.builder()
                    .addHttpListener(cport, chost)
                    .addHttpListener(pport, phost)
                    .setHandler(Handlers.path(mcmp).addPrefixPath("/mod_cluster_manager", web))
                    .build();
            server.start();

            // Start advertising the mcmp handler
            modCluster.advertise(config);

            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    modCluster.stop();
                    server.stop();

                }
            };
            Runtime.getRuntime().addShutdownHook(new Thread(r));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
