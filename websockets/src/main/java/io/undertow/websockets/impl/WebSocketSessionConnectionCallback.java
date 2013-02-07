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
    private final boolean executeInIoThread;


    public WebSocketSessionConnectionCallback(WebSocketSessionIdGenerator idGenerator, WebSocketSessionHandler sessionHandler) {
        this(idGenerator, sessionHandler, false);
    }

    public WebSocketSessionConnectionCallback(WebSocketSessionIdGenerator idGenerator, WebSocketSessionHandler sessionHandler, boolean executeInIoThread) {
        this.idGenerator = idGenerator;
        this.sessionHandler = sessionHandler;
        this.executeInIoThread = executeInIoThread;
    }

    @Override
    public void onConnect(HttpServerExchange exchange, WebSocketChannel channel) {
        final WebSocketChannelSession session = new WebSocketChannelSession(channel, idGenerator.nextId(), executeInIoThread);
        sessionHandler.onSession(session);

        channel.getReceiveSetter().set(new FrameHandlerDelegateListener(session));
        channel.resumeReceives();

    }

    private static void handleError(final WebSocketChannelSession session, final Throwable cause) {
        if (session.executeInIoThread) {
            session.getFrameHandler().onError(session, cause);
            IoUtils.safeClose(session.getChannel());
        } else {
            session.getFrameHandlerExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    session.getFrameHandler().onError(session, cause);
                    IoUtils.safeClose(session.getChannel());
                }
            });
        }
    }

    private final class FrameHandlerDelegateListener implements ChannelListener<WebSocketChannel> {
        private final WebSocketChannelSession session;
        private final EchoFrameHandlerListener defaultListener;
        boolean closeFrameReceived;

        FrameHandlerDelegateListener(WebSocketChannelSession session) {
            this.session = session;
            defaultListener = new EchoFrameHandlerListener(session, this);
        }

        @Override
        public void handleEvent(final WebSocketChannel webSocketChannel) {
            try {
                StreamSourceFrameChannel frame = webSocketChannel.receive();
                if (frame == null) {
                    webSocketChannel.resumeReceives();
                    return;
                }
                if (closeFrameReceived) {
                    frame.discard();
                    return;
                }

                long maxSize = session.getMaximumFrameSize();
                if (maxSize > 0 && (frame.getType() == WebSocketFrameType.BINARY || frame.getType() == WebSocketFrameType.TEXT)
                        && frame.getPayloadSize() > maxSize) {
                    if (executeInIoThread) {
                        session.sendClose(new CloseReason(CloseReason.MSG_TOO_BIG, null), null);
                    } else {
                        session.getFrameHandlerExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                session.sendClose(new CloseReason(CloseReason.MSG_TOO_BIG, null), null);
                            }
                        });
                    }
                    return;
                }

                // suspend the receives we will resume once we are ready
                webSocketChannel.suspendReceives();

                ChannelListener<StreamSourceChannel> listener;
                FrameHandler handler = session.getFrameHandler();
                if (handler == null) {
                    // no handler defined by the user use the default listener which takes care
                    // of echo back PING and CLOSE Frame to be RFC compliant
                    listener = defaultListener;
                } else if (handler instanceof AssembledFrameHandler) {
                    listener = new AssembleFrameChannelListener(session, (AssembledFrameHandler) handler, this, frame);
                }  else if (handler instanceof FragmentedFrameHandler) {
                    listener = new FragmentedFrameChannelListener(session, (FragmentedFrameHandler) handler, this);
                } else {
                    listener = new FrameHandlerListener(session,  handler, this);
                }

                frame.getReadSetter().set(listener);
                // wake up reads to trigger a read operation now
                // TODO: Think about if this a really good idea
                frame.wakeupReads();

            } catch (IOException e) {
                handleError(session, e);
            }
        }
    }

    private static final class FragmentedFrameChannelListener extends FrameHandlerListener {
        private WebSocketFrameType type;
        private List<Pooled<ByteBuffer>> pooledList;
        private final FragmentedFrameHandler handler;
        private Pooled<ByteBuffer> pooled;
        private final Pool<ByteBuffer> pool;

        private FragmentedFrameChannelListener(WebSocketChannelSession session, FragmentedFrameHandler handler, FrameHandlerDelegateListener delegateListener) {
            super(session, handler, delegateListener);
            this.handler = handler;
            pool = session.getChannel().getBufferPool();
        }

        @Override
        public void handleEvent(StreamSourceChannel ch) {
            StreamSourceFrameChannel streamSourceFrameChannel = (StreamSourceFrameChannel) ch;
            WebSocketFrameType type = streamSourceFrameChannel.getType();

            switch (type) {
                case TEXT:
                case BINARY:
                case CONTINUATION:
                    if (type == WebSocketFrameType.CONTINUATION) {
                        assert this.type != null;
                        type = this.type;
                    }
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

                                if (!streamSourceFrameChannel.isFinalFragment()) {
                                    // not the final fragement contine to handle it with this handler
                                    session.getChannel().getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
                                        @Override
                                        public void handleEvent(WebSocketChannel webSocketChannel) {
                                            boolean free = true;
                                            try {
                                                StreamSourceFrameChannel frame = webSocketChannel.receive();
                                                if (frame != null) {
                                                    // suspend receives we will resume once ready
                                                    webSocketChannel.suspendReceives();

                                                    frame.getReadSetter().set(FragmentedFrameChannelListener.this);

                                                    // wake up reads to trigger a read operation now
                                                    // TODO: Think about if this a really good idea
                                                    frame.wakeupReads();
                                                } else {
                                                    webSocketChannel.resumeReceives();
                                                }
                                                free = false;

                                            } catch (IOException e) {
                                                handleError(session, e);
                                            } finally {
                                                if (free) {
                                                    free0();
                                                }
                                            }
                                        }
                                    });
                                } else {
                                    session.getChannel().getReceiveSetter().set(delegateListener);
                                }

                                WebSocketFrameHeader header = new DefaultWebSocketFrameHeader(streamSourceFrameChannel.getType(), streamSourceFrameChannel.getRsv(), streamSourceFrameChannel.isFinalFragment());

                                if (pooledList != null) {
                                    pooledList.add(pooled);
                                    notifyHandler(session, handler, type, header, pooledList.toArray(new Pooled[0]));
                                } else {
                                    notifyHandler(session, handler, type, header, pooled);
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
                        handleError(session, e);
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

        private void notifyHandler(final WebSocketChannelSession session, final FragmentedFrameHandler handler, final WebSocketFrameType type, final WebSocketFrameHeader header, final Pooled<ByteBuffer>... pooled) {
            if (session.executeInIoThread)  {
                notifyHandler0(session, handler, type, header, pooled);
            } else {
                session.getFrameHandlerExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        notifyHandler0(session, handler, type, header, pooled);
                    }
                });
            }
        }

        private void notifyHandler0(WebSocketChannelSession session, FragmentedFrameHandler handler, WebSocketFrameType type, WebSocketFrameHeader header, Pooled<ByteBuffer>... pooled) {
            try {
                final ByteBuffer[] buffers = new ByteBuffer[pooled.length];
                for (int i = 0; i < pooled.length; i++) {
                    buffers[i] = pooled[i].getResource();
                }

                switch (type) {
                    case BINARY:
                        handler.onBinaryFrame(session, header, buffers);
                        break;
                    case TEXT:
                        handler.onTextFrame(session, header, buffers);
                        break;
                    default:
                        throw new IllegalStateException();
                }

            } finally {
                free0();
            }

            // resume the receives
            session.getChannel().resumeReceives();
        }
    }

    private static class EchoFrameHandlerListener implements ChannelListener<StreamSourceChannel> {
        protected final WebSocketChannelSession session;
        private final FrameHandlerDelegateListener delegateListener;

        EchoFrameHandlerListener(WebSocketChannelSession session, FrameHandlerDelegateListener delegateListener) {
            this.session = session;
            this.delegateListener = delegateListener;
        }

        @Override
        public void handleEvent(StreamSourceChannel ch) {
            final StreamSourceFrameChannel streamSourceFrameChannel = (StreamSourceFrameChannel) ch;
            try {
                switch (streamSourceFrameChannel.getType()) {
                    case PING:
                    case CLOSE:
                        delegateListener.closeFrameReceived = true;
                        if (session.executeInIoThread) {
                            WebSocketUtils.echoFrame(session.getChannel(), streamSourceFrameChannel);
                            session.getChannel().resumeReceives();
                        } else {
                            session.getFrameHandlerExecutor().execute(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        WebSocketUtils.echoFrame(session.getChannel(), streamSourceFrameChannel);
                                        session.getChannel().resumeReceives();
                                    } catch (IOException e) {
                                        handleError(session, e);
                                        streamSourceFrameChannel.getReadSetter().set(null);
                                    }
                                }
                            });
                        }
                        break;
                    default:
                        // discard the frame as we are not interested in it.
                        streamSourceFrameChannel.discard();
                        streamSourceFrameChannel.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                            @Override
                            public void handleEvent(StreamSourceChannel channel) {
                                session.getChannel().resumeReceives();
                            }
                        });

                }
            } catch (IOException e) {
                handleError(session, e);
                streamSourceFrameChannel.getReadSetter().set(null);
            }
        }
    }

    private static class FrameHandlerListener implements ChannelListener<StreamSourceChannel> {
        protected final WebSocketChannelSession session;
        private final FrameHandler handler;
        private Pooled<ByteBuffer> pooled;
        private List<Pooled<ByteBuffer>> pooledList;
        protected final FrameHandlerDelegateListener delegateListener;

        FrameHandlerListener(WebSocketChannelSession session,  FrameHandler handler, FrameHandlerDelegateListener delegateListener) {
            this.session = session;
            this.handler = handler;
            this.delegateListener = delegateListener;
        }

        @Override
        public void handleEvent(StreamSourceChannel streamSourceChannel) {
            StreamSourceFrameChannel streamSourceFrameChannel = (StreamSourceFrameChannel) streamSourceChannel;
            if (pooled == null) {
                pooled = session.getChannel().getBufferPool().allocate();
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

                        final ByteBuffer[] buffers;
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
                                final ByteBuffer[] payload = new ByteBuffer[buffers.length];
                                for (int i = 0; i < buffers.length; i++) {
                                    ByteBuffer buf = buffers[i];
                                    payload[i] = buf.slice();
                                }
                                if (session.executeInIoThread) {
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
                                    session.getChannel().resumeReceives();
                                } else {
                                    session.getFrameHandlerExecutor().execute(new Runnable() {
                                        @Override
                                        public void run() {
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
                                            session.getChannel().resumeReceives();
                                        }
                                    });
                                }

                                free = false;
                                return;
                            case PONG:
                                if (session.executeInIoThread) {
                                    handler.onPongFrame(session, buffers);
                                    session.getChannel().resumeReceives();
                                } else {
                                    session.getFrameHandlerExecutor().execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                handler.onPongFrame(session, buffers);
                                                session.getChannel().resumeReceives();
                                            } finally {
                                                free0();
                                            }
                                        }
                                    });
                                    free = false;

                                }
                                return;
                            case CLOSE:
                                delegateListener.closeFrameReceived = true;
                                final CloseReason reason;

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
                                if (session.executeInIoThread) {
                                    handler.onCloseFrame(session, reason);
                                    session.sendClose(reason, null);
                                    session.getChannel().resumeReceives();
                                } else {
                                    session.getFrameHandlerExecutor().execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            handler.onCloseFrame(session, reason);
                                            session.sendClose(reason, null);
                                            session.getChannel().resumeReceives();
                                        }
                                    });
                                }
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
                        pooled = session.getChannel().getBufferPool().allocate();
                    }
                }
            } catch (IOException e) {
                handleError(session, e);
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
        private final AssembledFrameHandler handler;
        private long size;
        private long maxSize;
        private boolean frameInProgress;
        AssembleFrameChannelListener(WebSocketChannelSession session, AssembledFrameHandler handler, FrameHandlerDelegateListener delegateListener, StreamSourceFrameChannel source) {
            super(session, handler, delegateListener);
            this.handler = handler;
            pool = session.getChannel().getBufferPool();
            header = new DefaultWebSocketFrameHeader(source.getType(), source.getRsv(), true);
            pooled = pool.allocate();
            maxSize = session.getMaximumFrameSize();
        }

        @Override
        public void handleEvent(StreamSourceChannel ch) {
            StreamSourceFrameChannel streamSourceFrameChannel = (StreamSourceFrameChannel) ch;
            switch (streamSourceFrameChannel.getType()) {
                case TEXT:
                case BINARY:
                case CONTINUATION:
                    boolean free = true;

                    if (!frameInProgress) {
                        frameInProgress = true;
                        size += streamSourceFrameChannel.getPayloadSize();

                        // this also match for TEXT frames
                        if (maxSize > 0 && size > maxSize) {
                            if (executeInIoThread) {
                                session.sendClose(new CloseReason(CloseReason.MSG_TOO_BIG, null), null);
                            } else {
                                session.getFrameHandlerExecutor().execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        session.sendClose(new CloseReason(CloseReason.MSG_TOO_BIG, null), null);
                                    }
                                });
                            }
                            return;
                        }

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
                                frameInProgress = false;
                                streamSourceFrameChannel.close();
                                streamSourceFrameChannel.getReadSetter().set(null);
                                buffer.flip();
                                if (pooledList != null) {
                                    pooledList.add(pooled);
                                }

                                if (streamSourceFrameChannel.isFinalFragment()) {
                                    session.getChannel().getReceiveSetter().set(delegateListener);

                                    // final fragement notify the handler now
                                    if (pooledList != null) {
                                        notifyHandler(session, handler, header, pooledList.toArray(new Pooled[0]));
                                        free = false;
                                    } else {
                                        notifyHandler(session, handler, header, pooled);
                                        free = false;
                                    }
                                } else {
                                    // not the final fragement keep buffer the payload
                                    session.getChannel().getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
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
                                                } else {
                                                    webSocketChannel.resumeReceives();
                                                }
                                                free = false;
                                            } catch (IOException e) {
                                                handleError(session, e);
                                            } finally {
                                                if (free) {
                                                    free0();
                                                }
                                            }

                                        }
                                    });
                                    free = false;
                                }

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
                        handleError(session, e);
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


        private void notifyHandler(final WebSocketChannelSession session, final AssembledFrameHandler handler, final WebSocketFrameHeader header, final Pooled<ByteBuffer>... pooled) {
            if (session.executeInIoThread) {
                notifyHandler0(session, handler, header, pooled);
            } else {
                session.getFrameHandlerExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        notifyHandler0(session, handler, header, pooled);
                    }
                });
            }
        }

        private void notifyHandler0(WebSocketChannelSession session, AssembledFrameHandler handler, WebSocketFrameHeader header, Pooled<ByteBuffer>... pooled) {
            try {
                final ByteBuffer[] buffers = new ByteBuffer[pooled.length];
                for (int i = 0; i < pooled.length; i++) {
                    buffers[i] = pooled[i].getResource();
                }

                switch (header.getType()) {
                    case BINARY:
                        handler.onBinaryFrame(session, header, buffers);
                        break;
                    case TEXT:
                        handler.onTextFrame(session, header, WebSocketUtils.toUtf8String(buffers));
                        break;
                    default:
                        throw new IllegalStateException();
                }
            } finally {
                free0();
            }

            // resume the receives
            session.getChannel().resumeReceives();
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
}
