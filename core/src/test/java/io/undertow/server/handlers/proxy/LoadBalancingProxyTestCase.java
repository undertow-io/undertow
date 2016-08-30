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
import io.undertow.predicate.Predicates;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.testutils.DefaultServer;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.xnio.Options;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Tests the load balancing proxy
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class LoadBalancingProxyTestCase extends AbstractLoadBalancingProxyTestCase {

    @BeforeClass
    public static void setup() throws URISyntaxException {

        int port = DefaultServer.getHostPort("default");
        server1 = Undertow.builder()
                .addHttpListener(port + 1, DefaultServer.getHostAddress("default"))
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(getRootHandler("s1", "server1"))
                .build();

        server2 = Undertow.builder()
                .addHttpListener(port + 2, DefaultServer.getHostAddress("default"))
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(getRootHandler("s2", "server2"))
                .build();
        server1.start();
        server2.start();

        ProxyHandler handler = new ProxyHandler(new LoadBalancingProxyClient()
                .setConnectionsPerThread(4)
                .addHost(new URI("http", null, DefaultServer.getHostAddress("default"), port + 1, null, null, null), "s1")
                .addHost(new URI("http", null, DefaultServer.getHostAddress("default"), port + 2, null, null, null), "s2")
                , 10000, ResponseCodeHandler.HANDLE_404, false, false, 1);

        DefaultServer.setRootHandler(new EncodingHandler(handler, new ContentEncodingRepository()
                .addEncodingHandler("gzip",
                        new GzipEncodingProvider(), 50,
                        Predicates.truePredicate())));
    }

}
