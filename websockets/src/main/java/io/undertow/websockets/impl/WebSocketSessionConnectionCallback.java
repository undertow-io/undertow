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

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.api.FragmentedFrameHandler;
import io.undertow.websockets.api.SendCallback;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketUtils;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.api.AssembledFrameHandler;
import io.undertow.websockets.api.CloseReason;
import io.undertow.websockets.api.FrameHandler;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.api.WebSocketSessionIdGenerator;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * {@link WebSocketConnectionCallback} which will create a {@link WebSocketSession} and operate on it.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class WebSocketSessionConnectionCallback implements WebSocketConnectionCallback {
    private final WebSocketSessionIdGenerator idGenerator;
    private final WebSocketSessionHandler sessionHandler;

    public WebSocketSessionConnectionCallback(WebSocketSessionIdGenerator idGenerator, WebSocketSessionHandler sessionHandler) {
        this.idGenerator = idGenerator;
        this.sessionHandler = sessionHandler;
    }

    @Override
    public void onConnect(HttpServerExchange exchange, WebSocketChannel channel) {
        final WebSocketChannelSession session = new WebSocketChannelSession(channel, idGenerator.nextId());
        sessionHandler.onSession(session);

        channel.getReceiveSetter().set(new FrameHandlerDelegateListener(session, channel));
        channel.resumeReceives();

    }

    private static void handleError(WebSocketSession session, WebSocketChannel channel, Throwable cause) {
        session.getFrameHandler().onError(session, cause);
        IoUtils.safeClose(channel);

    }

    private final class FrameHandlerDelegateListener implements ChannelListener<WebSocketChannel> {
        private final WebSocketSession session;
        private final EchoFrameHandlerListener defaultListener;
        FrameHandlerDelegateListener(WebSocketSession session, WebSocketChannel channel) {
            this.session = session;
            defaultListener = new EchoFrameHandlerListener(session, channel);
        }

        @Override
        public void handleEvent(final WebSocketChannel webSocketChannel) {
            try {
                StreamSourceFrameChannel frame = webSocketChannel.receive();
                if (frame == null) {
                    webSocketChannel.resumeReceives();
                    return;
                }

                ChannelListener<StreamSourceChannel> listener;
                FrameHandler handler = session.getFrameHandler();
                if (handler == null) {
                    // no handler defined by the user use the default listener which takes care
                    // of echo back PING and CLOSE Frame to be RFC compliant
                    listener = defaultListener;
                } else if (handler instanceof AssembledFrameHandler) {
                    listener = new AssembleFrameChannelListener(session, webSocketChannel, (AssembledFrameHandler) handler, this, frame);
                }  else if (handler instanceof FragmentedFrameHandler) {
                    listener = new FragmentedFrameChannelListener(session, webSocketChannel, (FragmentedFrameHandler) handler, this);
                } else {
                    listener = new FrameHandlerListener(session, webSocketChannel,  handler);
                }
                frame.getReadSetter().set(listener);
                // wake up reads to trigger a read operation now
                // TODO: Think about if this a really good idea
                frame.wakeupReads();

                webSocketChannel.resumeReceives();

            } catch (IOException e) {
                handleError(session, webSocketChannel, e);
            }
        }
    }

    private static final class FragmentedFrameChannelListener extends FrameHandlerListener {
        private WebSocketFrameType type;
        private List<Pooled<ByteBuffer>> pooledList;
        private final FragmentedFrameHandler handler;
        private Pooled<ByteBuffer> pooled;
        private final FrameHandlerDelegateListener delegateListener;
        private final Pool<ByteBuffer> pool;

        private FragmentedFrameChannelListener(WebSocketSession session, WebSocketChannel channel, FragmentedFrameHandler handler, FrameHandlerDelegateListener delegateListener) {
            super(session, channel, handler);
            this.handler = handler;
            this.delegateListener = delegateListener;
            pool = channel.getBufferPool();
        }

        @Override
        public void handleEvent(StreamSourceChannel ch) {
            StreamSourceFrameChannel streamSourceFrameChannel = (StreamSourceFrameChannel) ch;
            WebSocketFrameType type = streamSourceFrameChannel.getType();

            if (type == WebSocketFrameType.CONTINUATION) {
                assert type != null;
                type = this.type;
            }
            switch (type) {
                case TEXT:
                case BINARY:
                    this.type = type;
                    boolean free = true;
                    if (pooled == null) {
                        pooled = pool.allocate();
                    }
                    try {
                        for (;;) {
                            ByteBuffer buffer = pooled.getResource();

                            int r = streamSourceFrameChannel.read(buffer);
                            if (r == 0) {
                                free = false;
                                streamSourceFrameChannel.resumeReads();
                                return;
                            }
                            if (r == -1) {
                                streamSourceFrameChannel.getReadSetter().set(null);
                                streamSourceFrameChannel.close();
                                buffer.flip();
                                WebSocketFrameHeader header = new DefaultWebSocketFrameHeader(streamSourceFrameChannel.getType(), streamSourceFrameChannel.getRsv(), streamSourceFrameChannel.isFinalFragment());

                                if (pooledList != null) {
                                    pooledList.add(pooled);
                                    ByteBuffer[] buffers = new ByteBuffer[pooledList.size()];
                                    for (int i = 0; i < pooledList.size(); i++) {
                                        buffers[i] = pooledList.get(i).getResource();
                                    }
                                    notifyHandler(session, handler, type, header, buffers);
                                } else {
                                    notifyHandler(session, handler, type, header, buffer);
                                }

                                if (!streamSourceFrameChannel.isFinalFragment()) {
                                    // not the final fragement contine to handle it with this handler
                                    channel.getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
                                        @Override
                                        public void handleEvent(WebSocketChannel webSocketChannel) {
                                            boolean free = true;
                                            try {
                                                StreamSourceFrameChannel frame = webSocketChannel.receive();
                                                if (frame != null) {
                                                    frame.getReadSetter().set(FragmentedFrameChannelListener.this);

                                                    // wake up reads to trigger a read operation now
                                                    // TODO: Think about if this a really good idea
                                                    frame.wakeupReads();
                                                }
                                                webSocketChannel.resumeReceives();
                                                free = false;

                                            } catch (IOException e) {
                                                handleError(session, channel, e);
                                            } finally {
                                                if (free) {
                                                    free0();
                                                }
                                            }
                                        }
                                    });
                                } else {
                                    channel.getReceiveSetter().set(delegateListener);
                                }
                                channel.resumeReceives();
                                return;
                            }
                            if (!buffer.hasRemaining()) {
                                buffer.flip();
                                if (pooledList == null) {
                                    pooledList = new ArrayList<Pooled<ByteBuffer>>(2);
                                }
                                pooledList.add(pooled);
                                pooled = pool.allocate();
                            }
                        }
                    } catch (IOException e) {
                        handleError(session, channel, e);
                        streamSourceFrameChannel.getReadSetter().set(null);
                    } finally {
                        if (free) {
                            free0();
                        }
                    }
                    return;
                default:
                    super.handleEvent(streamSourceFrameChannel);
            }

        }

        private void free0() {
            free(pooled, pooledList);
            pooled = null;
            pooledList = null;
        }

        private static void notifyHandler(WebSocketSession session, FragmentedFrameHandler handler, WebSocketFrameType type, WebSocketFrameHeader header, ByteBuffer... payload) {
            switch (type) {
                case BINARY:
                    handler.onBinaryFrame(session, header, payload);
                    return;
                case TEXT:
                    handler.onTextFrame(session, header, payload);
                    return;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    private static class EchoFrameHandlerListener implements ChannelListener<StreamSourceChannel> {
        protected final WebSocketSession session;
        protected final WebSocketChannel channel;

        EchoFrameHandlerListener(WebSocketSession session, WebSocketChannel channel) {
            this.session = session;
            this.channel = channel;
        }

        @Override
        public void handleEvent(StreamSourceChannel ch) {
            StreamSourceFrameChannel streamSourceFrameChannel = (StreamSourceFrameChannel) ch;
            try {
                switch (streamSourceFrameChannel.getType()) {
                    case PING:
                    case CLOSE:
                        WebSocketUtils.echoFrame(channel, streamSourceFrameChannel);
                        return;
                    default:
                        // discard the frame as we are not interested in it.
                        streamSourceFrameChannel.discard();

                }
            } catch (IOException e) {
                handleError(session, channel, e);
                streamSourceFrameChannel.getReadSetter().set(null);
            }
        }
    }

    private static class FrameHandlerListener implements ChannelListener<StreamSourceChannel> {
        protected final WebSocketSession session;
        protected final WebSocketChannel channel;
        private final FrameHandler handler;
        private Pooled<ByteBuffer> pooled;
        private List<Pooled<ByteBuffer>> pooledList;

        FrameHandlerListener(WebSocketSession session, WebSocketChannel channel, FrameHandler handler) {
            this.session = session;
            this.channel = channel;
            this.handler = handler;
        }

        @Override
        public void handleEvent(StreamSourceChannel streamSourceChannel) {
            StreamSourceFrameChannel streamSourceFrameChannel = (StreamSourceFrameChannel) streamSourceChannel;
            if (pooled == null) {
                pooled = channel.getBufferPool().allocate();
            }
            boolean free = true;
            try {
                for (;;) {
                    ByteBuffer buffer = pooled.getResource();

                    int r = streamSourceChannel.read(buffer);
                    if (r == 0) {
                        streamSourceChannel.resumeReads();
                        free = false;
                        return;
                    }
                    if (r == -1) {
                        buffer.flip();
                        streamSourceChannel.close();
                        streamSourceChannel.getReadSetter().set(null);

                        ByteBuffer[] buffers;
                        if (pooledList != null) {
                            pooledList.add(pooled);
                            buffers = new ByteBuffer[pooledList.size()];
                            for (int i = 0; i < pooledList.size(); i++) {
                                buffers[i] = pooledList.get(i).getResource();
                            }
                        } else {
                           buffers = new ByteBuffer[] {buffer};
                        }

                        switch (streamSourceFrameChannel.getType()) {
                            case PING:
                                ByteBuffer[] payload = new ByteBuffer[buffers.length];
                                for (int i = 0; i < buffers.length; i++) {
                                    ByteBuffer buf = buffers[i];
                                    payload[i] = buf.slice();
                                }
                                handler.onPingFrame(session, payload);
                                session.sendPong(buffers, new SendCallback() {
                                    @Override
                                    public void onCompletion() {
                                        free0();
                                    }

                                    @Override
                                    public void onError(Throwable cause) {
                                        free0();
                                    }
                                });
                                free = false;
                                return;
                            case PONG:
                                handler.onPongFrame(session, buffer);
                                return;
                            case CLOSE:
                                CloseReason reason;

                                // we asume at least the status code is in the first frame which should be ok
                                if (buffers[0].hasRemaining()) {
                                    int code = buffers[0].getShort();
                                    String text;
                                    if (StreamSinkChannelUtils.payloadLength(buffers) > 0) {
                                        text = WebSocketUtils.toUtf8String(buffers);
                                    } else {
                                        text = null;
                                    }
                                    reason = new CloseReason(code, text);
                                } else {
                                    reason = null;
                                }
                                handler.onCloseFrame(session, reason);
                                session.sendClose(reason, null);
                                return;
                            default:
                                return;
                        }

                    }
                    if (!buffer.hasRemaining()) {
                        buffer.flip();
                        if (pooledList == null) {
                            pooledList = new ArrayList<Pooled<ByteBuffer>>(2);
                        }
                        pooledList.add(pooled);
                        pooled = channel.getBufferPool().allocate();
                    }
                }
            } catch (IOException e) {
                handleError(session, channel, e);
                streamSourceChannel.getReadSetter().set(null);

            } finally {
                if (free) {
                    free0();
                }
            }
        }

        private void free0() {
            free(pooled, pooledList);
            pooled = null;
            pooledList = null;
        }
    }

    private final class AssembleFrameChannelListener extends FrameHandlerListener {
        private final Pool<ByteBuffer> pool;
        private ArrayList<Pooled<ByteBuffer>> pooledList;
        private Pooled<ByteBuffer> pooled;
        private final WebSocketFrameHeader header;
        private final FrameHandlerDelegateListener frameListener;
        private final AssembledFrameHandler handler;

        AssembleFrameChannelListener(WebSocketSession session,WebSocketChannel channel, AssembledFrameHandler handler, FrameHandlerDelegateListener frameListener, StreamSourceFrameChannel source) {
            super(session, channel, handler);
            this.handler = handler;
            pool = channel.getBufferPool();
            header = new DefaultWebSocketFrameHeader(source.getType(), source.getRsv(), true);
            pooled = pool.allocate();
            this.frameListener = frameListener;
        }

        @Override
        public void handleEvent(StreamSourceChannel ch) {
            StreamSourceFrameChannel streamSourceFrameChannel = (StreamSourceFrameChannel) ch;

            switch (streamSourceFrameChannel.getType()) {
                case TEXT:
                case BINARY:
                case CONTINUATION:
                    boolean free = true;
                    try {
                        for (;;) {
                            ByteBuffer buffer = pooled.getResource();

                            int r = streamSourceFrameChannel.read(buffer);
                            if (r == 0) {
                                free = false;
                                streamSourceFrameChannel.resumeReads();
                                return;
                            }
                            if (r == -1) {
                                streamSourceFrameChannel.close();
                                streamSourceFrameChannel.getReadSetter().set(null);
                                buffer.flip();
                                if (pooledList != null) {
                                    pooledList.add(pooled);
                                }

                                if (streamSourceFrameChannel.isFinalFragment()) {
                                    // final fragement notify the handler now
                                    if (pooledList != null) {
                                        ByteBuffer[] buffers = new ByteBuffer[pooledList.size()];
                                        for (int i = 0; i < pooledList.size(); i++) {
                                            buffers[i] = pooledList.get(i).getResource();
                                        }
                                        notifyHandler(session, handler, header, buffers);
                                    } else {
                                        notifyHandler(session, handler, header, buffer);
                                    }
                                    channel.getReceiveSetter().set(frameListener);
                                } else {
                                    // not the final fragement keep buffer the payload
                                    channel.getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
                                        @Override
                                        public void handleEvent(WebSocketChannel webSocketChannel) {
                                            boolean free = true;
                                            try {
                                                StreamSourceFrameChannel frame = webSocketChannel.receive();
                                                if (frame != null) {
                                                    frame.getReadSetter().set(AssembleFrameChannelListener.this);
                                                    // wake up reads to trigger a read operation now
                                                    // TODO: Think about if this a really good idea
                                                    frame.wakeupReads();
                                                }
                                                webSocketChannel.resumeReceives();
                                                free = false;
                                            } catch (IOException e) {
                                                handleError(session, channel, e);
                                            } finally {
                                                if (free) {
                                                    free0();
                                                }
                                            }

                                        }
                                    });
                                }
                                free = false;

                                return;
                            }
                            if (!buffer.hasRemaining()) {
                                buffer.flip();
                                if (pooledList == null) {
                                    pooledList = new ArrayList<Pooled<ByteBuffer>>(2);
                                }
                                pooledList.add(pooled);
                                pooled = pool.allocate();
                            }
                        }
                    } catch (IOException e) {
                        handleError(session, channel, e);
                        streamSourceFrameChannel.getReadSetter().set(null);
                    } finally {
                        if (free) {
                            free0();
                        }
                    }
                    return;
                default:
                    super.handleEvent(streamSourceFrameChannel);
            }

        }

        private void free0() {
            free(pooled, pooledList);
            pooled = null;
            pooledList = null;
        }

    }

    private static void free(Pooled<ByteBuffer> pooled, List<Pooled<ByteBuffer>> pooledList) {
        if (pooledList != null) {
            for (Pooled<ByteBuffer> p: pooledList) {
                p.free();
            }
        }
        if (pooled != null) {
            pooled.free();
        }
    }

    private static void notifyHandler(WebSocketSession session, AssembledFrameHandler handler, WebSocketFrameHeader header, ByteBuffer... payload) {
        switch (header.getType()) {
            case BINARY:
                handler.onBinaryFrame(session, header, payload);
                return;
            case TEXT:
                handler.onTextFrame(session, header, WebSocketUtils.toUtf8String(payload));
                return;
            default:
                throw new IllegalStateException();
        }
    }
}
