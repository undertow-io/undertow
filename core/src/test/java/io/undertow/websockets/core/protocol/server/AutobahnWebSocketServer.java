/*
 * Copyright 2012 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.core.protocol.server;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpOpenListener;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketUtils;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.core.handler.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;

import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * This class is intended for use with testing against the Python
 * <a href="http://www.tavendo.de/autobahn/testsuite.html">AutoBahn test suite</a>.
 *
 * Autobahn installation documentation can be found <a href="http://autobahn.ws/testsuite/installation">here</a>.
 *
 * <h3>How to run the tests on Linux/OSX.</h3>
 *
 * <p>01. Install AutoBahn: <tt>sudo easy_install autobahntestsuite</tt>.  Test using <tt>wstest --help</tt>.
 *
 * <p>02. Create a directory for test configuration and results: <tt>mkdir ~/autobahn</tt> <tt>cd ~/autobahn</tt>.
 *
 * <p>03. Create <tt>fuzzing_client_spec.json</tt> in the above directory
 * {@code
 * {
 *    "options": {"failByDrop": false},
 *    "outdir": "./reports/servers",
 *
 *    "servers": [
 *                 {"agent": "Netty4",
 *                  "url": "ws://localhost:9000",
 *                  "options": {"version": 18}}
 *               ],
 *
 *    "cases": ["*"],
 *    "exclude-cases": ["9.*"],
 *    "exclude-agent-cases": {}
 * }
 * }
 * Note that we disabled the <strong>9.*</strong> tests for now as these fail.
 *
 * <p>04. Run the <tt>AutobahnServer</tt> located in this package. If you are in Eclipse IDE, right click on
 * <tt>AutobahnServer.java</tt> and select Run As > Java Application.
 *
 * <p>05. Run the Autobahn test <tt>wstest -m fuzzingclient -s fuzzingclient.json</tt>.
 *
 * <p>06. See the results in <tt>./reports/servers/index.html</tt>
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class AutobahnWebSocketServer {
    private HttpOpenListener openListener;
    private XnioWorker worker;
    private AcceptingChannel<StreamConnection> server;
    private Xnio xnio;
    private final int port;

    public AutobahnWebSocketServer(int port) {
        this.port = port;
    }


    public void run() {
        xnio = Xnio.getInstance("nio");
        try {
            worker = xnio.createWorker(OptionMap.builder()
                    .set(Options.WORKER_WRITE_THREADS, 4)
                    .set(Options.WORKER_READ_THREADS, 4)
                    .set(Options.CONNECTION_HIGH_WATER, 1000000)
                    .set(Options.CONNECTION_LOW_WATER, 1000000)
                    .set(Options.WORKER_TASK_CORE_THREADS, 10)
                    .set(Options.WORKER_TASK_MAX_THREADS, 12)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.CORK, true)
                    .getMap());

            OptionMap serverOptions = OptionMap.builder()
                    .set(Options.WORKER_ACCEPT_THREADS, 4)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true)
                    .getMap();
            openListener = new HttpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 8192 * 8192), 8192);
            ChannelListener acceptListener = ChannelListeners.openListenerAdapter(openListener);
            server = worker.createStreamConnectionServer(new InetSocketAddress(port), acceptListener, serverOptions);


            setRootHandler(getRootHandler());
            server.resumeAccepts();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static WebSocketProtocolHandshakeHandler getRootHandler() {
        return new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                channel.getReceiveSetter().set(new Receiver());
                channel.resumeReceives();
            }
        });
    }

    private static final class Receiver implements ChannelListener<WebSocketChannel> {

        @Override
        public void handleEvent(final WebSocketChannel channel) {
            try {
                final StreamSourceFrameChannel ws = channel.receive();
                if (ws != null) {
                    WebSocketUtils.echoFrame(channel, ws);
                }
                channel.resumeReceives();
            } catch (IOException e) {
                //e.printStackTrace();
                IoUtils.safeClose(channel);
            }
        }
    }

    /**
     * Sets the root handler for the default web server
     *
     * @param rootHandler The handler to use
     */
    private void setRootHandler(HttpHandler rootHandler) {
        openListener.setRootHandler(rootHandler);
    }

    public static void main(String[] args) {
        new AutobahnWebSocketServer(7777).run();
    }


}
