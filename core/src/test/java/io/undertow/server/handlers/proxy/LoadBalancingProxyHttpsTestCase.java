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

package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;
import org.xnio.Options;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Tests the load balancing proxy
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class LoadBalancingProxyHttpsTestCase extends AbstractLoadBalancingProxyTestCase {

    @BeforeClass
    public static void setup() throws URISyntaxException {

        final SessionCookieConfig sessionConfig = new SessionCookieConfig();
        int port = DefaultServer.getHostPort("default");
        server1 = Undertow.builder()
                .addHttpsListener(port + 1, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(getRootHandler("s1", "server1"))
                .build();
        server2 = Undertow.builder()
                .addHttpsListener(port + 2, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_SPDY, false)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(getRootHandler("s2", "server2"))
                .build();
        server1.start();
        server2.start();

        UndertowXnioSsl ssl = new UndertowXnioSsl(DefaultServer.getWorker().getXnio(), OptionMap.EMPTY, DefaultServer.SSL_BUFFER_POOL, DefaultServer.createClientSslContext());
        DefaultServer.setRootHandler(new ProxyHandler(new LoadBalancingProxyClient()
                .setConnectionsPerThread(4)
                .addHost(new URI("https", null, DefaultServer.getHostAddress("default"), port + 1, null, null, null), "s1", ssl)
                .addHost(new URI("https", null, DefaultServer.getHostAddress("default"), port + 2, null, null, null), "s2", ssl)
                , 10000, ResponseCodeHandler.HANDLE_404, false, false , 2));
    }

}
