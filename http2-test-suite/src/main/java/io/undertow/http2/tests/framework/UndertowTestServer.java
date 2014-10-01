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

package io.undertow.http2.tests.framework;

import io.undertow.UndertowOptions;
import io.undertow.server.OpenListener;
import io.undertow.server.protocol.http2.Http2OpenListener;
import org.jboss.logging.Logger;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.XnioSsl;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;

import static io.undertow.http2.tests.framework.Http2TestRunner.BUFFER_SIZE;

/**
 * @author Stuart Douglas
 */
public class UndertowTestServer implements ServerController {


    private static OpenListener openListener;
    private static ChannelListener acceptListener;
    private static XnioWorker worker;
    private static AcceptingChannel<? extends StreamConnection> server;
    private static Xnio xnio;


    private static final Logger log = Logger.getLogger(UndertowTestServer.class);

    @Override
    public void start(String host, int httpPort, int httpsPort) {
        xnio = Xnio.getInstance("nio", UndertowTestServer.class.getClassLoader());
        try {
            worker = xnio.createWorker(OptionMap.builder()
                    .set(Options.WORKER_IO_THREADS, 8)
                    .set(Options.CONNECTION_HIGH_WATER, 1000000)
                    .set(Options.CONNECTION_LOW_WATER, 1000000)
                    .set(Options.WORKER_TASK_CORE_THREADS, 30)
                    .set(Options.WORKER_TASK_MAX_THREADS, 30)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.CORK, true)
                    .getMap());

            OptionMap serverOptions = OptionMap.builder()
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true)
                    .set(Options.BALANCING_TOKENS, 1)
                    .set(Options.BALANCING_CONNECTIONS, 2)
                    .getMap();

            openListener = new Http2OpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 2 * BUFFER_SIZE, 100 * BUFFER_SIZE), OptionMap.create(UndertowOptions.ENABLE_SPDY, true));
            acceptListener = ChannelListeners.openListenerAdapter(openListener);

            SSLContext serverContext = Http2TestRunner.getServerSslContext();
            XnioSsl xnioSsl = new JsseXnioSsl(xnio, OptionMap.EMPTY, serverContext);
            server = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(TestEnvironment.getHost(), TestEnvironment.getPort()), acceptListener, serverOptions);
            server.resumeAccepts();
            openListener.setRootHandler(new UndertowTestHandler());
            server.resumeAccepts();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {

    }


}
