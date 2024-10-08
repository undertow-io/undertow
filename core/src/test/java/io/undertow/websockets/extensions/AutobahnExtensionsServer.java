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

package io.undertow.websockets.extensions;

import java.io.IOException;
import java.net.InetSocketAddress;

import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.apache.log4j.BasicConfigurator;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;

/**
 * A WebSocket Server implementation for use with <a href="http://www.tavendo.de/autobahn/testsuite.html">AutoBahn test suite</a>.
 * <p>
 * A variant of {@link io.undertow.websockets.core.protocol.server.AutobahnWebSocketServer} but focus in extensions capabilities.
 * <p>
 * It uses {@link DebugExtensionsHeaderHandler} and {@link DebugExtensionsListener} for WebSocket handler and listener.
 *
 * @author Lucas Ponce
 */
public class AutobahnExtensionsServer {
    private HttpOpenListener openListener;
    private XnioWorker worker;
    private AcceptingChannel<StreamConnection> server;
    private Xnio xnio;
    private final int port;

    public static WebSocketProtocolHandshakeHandler webSocketDebugHandler() {
        return new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                WebSocketLogger.EXTENSION_LOGGER.info("onConnect() ");
                channel.getReceiveSetter().set(new DebugExtensionsListener());
                channel.resumeReceives();
            }
        });
    }

    public AutobahnExtensionsServer(int port) {
        this.port = port;
    }

    public void run() {
        xnio = Xnio.getInstance();
        try {
            worker = xnio.createWorker(OptionMap.builder()
                    .set(Options.CONNECTION_HIGH_WATER, 1000000)
                    .set(Options.CONNECTION_LOW_WATER, 1000000)
                    .set(Options.WORKER_TASK_CORE_THREADS, 10)
                    .set(Options.WORKER_TASK_MAX_THREADS, 12)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.CORK, true)
                    .getMap());

            OptionMap serverOptions = OptionMap.builder()
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true)
                    .getMap();
            openListener = new HttpOpenListener(new DefaultByteBufferPool(false, 8192));
            ChannelListener acceptListener = ChannelListeners.openListenerAdapter(openListener);
            server = worker.createStreamConnectionServer(new InetSocketAddress(port), acceptListener, serverOptions);

            WebSocketProtocolHandshakeHandler handler = webSocketDebugHandler()
                    .addExtension(new PerMessageDeflateHandshake());

            DebugExtensionsHeaderHandler debug = new DebugExtensionsHeaderHandler(handler);

            setRootHandler(debug);
            server.resumeAccepts();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setRootHandler(HttpHandler rootHandler) {
        openListener.setRootHandler(rootHandler);
    }

    public static void main(String[] args) {
        /*
            Use BasicConfigurator.configure() for fully debug
         */
        if (args.length == 1) {
            if (args[0].equals("--debug")) {
                BasicConfigurator.configure();
            }
        }
        new AutobahnExtensionsServer(7777).run();
    }
}
