/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.impl;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpOpenListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.api.AbstractFragmentedFrameHandler;
import io.undertow.websockets.core.handler.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.api.FragmentedBinaryFrameSender;
import io.undertow.websockets.api.FragmentedTextFrameSender;
import io.undertow.websockets.api.SendCallback;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.api.WebSocketSessionHandler;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class HighLevelAutobahnWebSocketServer {
    private HttpOpenListener openListener;
    private XnioWorker worker;
    private AcceptingChannel<? extends ConnectedStreamChannel> server;
    private Xnio xnio;
    private final int port;

    private static final SendCallback PRINT_ERROR_SEND_CALLBACK = new SendCallback() {
        @Override
        public void onCompletion() {
            // NOOP
        }

        @Override
        public void onError(Throwable cause) {
            cause.printStackTrace();
        }
    };

    public HighLevelAutobahnWebSocketServer(int port) {
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
            server = worker.createStreamServer(new InetSocketAddress(port), acceptListener, serverOptions);


            setRootHandler(new WebSocketProtocolHandshakeHandler(
                    new WebSocketSessionConnectionCallback(new UuidWebSocketSessionIdGenerator(),
                            new WebSocketSessionHandlerImpl(), false)));
            server.resumeAccepts();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        new HighLevelAutobahnWebSocketServer(7777).run();
    }

    private static final class WebSocketSessionHandlerImpl implements WebSocketSessionHandler {
        @Override
        public void onSession(WebSocketSession session, HttpServerExchange exchange) {
            session.setFrameHandler(new AbstractFragmentedFrameHandler() {
                private FragmentedBinaryFrameSender binaryFrameSender;
                private FragmentedTextFrameSender textFrameSender;

                @Override
                public void onTextFrame(WebSocketSession session, WebSocketFrameHeader header, ByteBuffer... payload) {
                    FragmentedTextFrameSender textFrameSender = this.textFrameSender;

                    if (textFrameSender == null) {
                        textFrameSender = this.textFrameSender = session.sendFragmentedText();
                    }
                    if (header.isLastFragement()) {
                        textFrameSender.finalFragment();
                        this.textFrameSender = null;

                    }
                    textFrameSender.sendText(copy(payload), PRINT_ERROR_SEND_CALLBACK);

                }

                @Override
                public void onBinaryFrame(WebSocketSession session, WebSocketFrameHeader header, ByteBuffer... payload) {
                    FragmentedBinaryFrameSender binaryFrameSender = this.binaryFrameSender;
                    if (binaryFrameSender == null) {
                        binaryFrameSender =  this.binaryFrameSender = session.sendFragmentedBinary();
                    }
                    if (header.isLastFragement()) {
                        binaryFrameSender.finalFragment();
                        this.binaryFrameSender = null;

                    }
                    binaryFrameSender.sendBinary(copy(payload), PRINT_ERROR_SEND_CALLBACK);
                }

                @Override
                public void onPongFrame(WebSocketSession session, ByteBuffer... payload) {
                    System.out.println("PONG!!!");
                }

                @Override
                public void onError(WebSocketSession session, Throwable cause) {
                    cause.printStackTrace();
                }

                private ByteBuffer[] copy(ByteBuffer... payload) {

                    ByteBuffer[] buffers = new ByteBuffer[payload.length];
                    for (int i = 0; i < payload.length; i++) {
                        ByteBuffer src = payload[i];
                        ByteBuffer buffer = ByteBuffer.allocate(src.remaining());
                        buffer.put(src);
                        buffer.flip();
                        buffers[i] = buffer;
                    }
                    return buffers;
                }
            });
        }
    }

}
