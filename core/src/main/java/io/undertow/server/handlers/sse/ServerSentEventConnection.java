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

package io.undertow.server.handlers.sse;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Attachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import io.undertow.util.HeaderMap;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Represents the server side of a Server Sent Events connection.
 *
 * The class implements Attachable, which provides access to the underlying exchanges attachments.
 *
 * @author Stuart Douglas
 */
public class ServerSentEventConnection implements Channel, Attachable {

    private final HttpServerExchange exchange;
    private final StreamSinkChannel sink;
    private final SseWriteListener writeListener = new SseWriteListener();

    private Pooled<ByteBuffer> pooled;

    private final Queue<SSEData> queue = new ConcurrentLinkedDeque<>();
    private final List<SSEData> buffered = new ArrayList<>();
    private final List<ChannelListener<ServerSentEventConnection>> closeTasks = new CopyOnWriteArrayList<>();
    private Map<String, String> parameters;
    private Map<String, Object> properties = new HashMap<>();

    private static final AtomicIntegerFieldUpdater<ServerSentEventConnection> openUpdater = AtomicIntegerFieldUpdater.newUpdater(ServerSentEventConnection.class, "open");
    private volatile int open = 1;
    private volatile boolean shutdown = false;
    private volatile long keepAliveTime = -1;
    private XnioExecutor.Key timerKey;


    public ServerSentEventConnection(HttpServerExchange exchange, StreamSinkChannel sink) {
        this.exchange = exchange;
        this.sink = sink;
        this.sink.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
            @Override
            public void handleEvent(StreamSinkChannel channel) {
                if(timerKey != null) {
                    timerKey.remove();
                }
                for (ChannelListener<ServerSentEventConnection> listener : closeTasks) {
                    ChannelListeners.invokeChannelListener(ServerSentEventConnection.this, listener);
                }
            }
        });
        this.sink.getWriteSetter().set(writeListener);
    }

    /**
     * Adds a listener that will be invoked when the channel is closed
     *
     * @param listener The listener to invoke
     */
    public void addCloseTask(ChannelListener<ServerSentEventConnection> listener) {
        this.closeTasks.add(listener);
    }

    /**
     *
     * @return The principal that was associated with the SSE request
     */
    public Principal getPrincipal() {
        Account account = getAccount();
        if (account != null) {
            return account.getPrincipal();
        }
        return null;
    }

    /**
     *
     * @return The account that was associated with the SSE request
     */
    public Account getAccount() {
        SecurityContext sc = exchange.getSecurityContext();
        if (sc != null) {
            return sc.getAuthenticatedAccount();
        }
        return null;
    }

    /**
     *
     * @return The request headers from the initial request that opened this connection
     */
    public HeaderMap getRequestHeaders() {
        return exchange.getRequestHeaders();
    }

    /**
     *
     * @return The response headers from the initial request that opened this connection
     */
    public HeaderMap getResponseHeaders() {
        return exchange.getResponseHeaders();
    }

    /**
     *
     * @return The request URI from the initial request that opened this connection
     */
    public String getRequestURI() {
        return exchange.getRequestURI();
    }

    /**
     * Sends an event to the remote client
     *
     * @param data The event data
     */
    public void send(String data) {
        send(data, null, null, null);
    }

    /**
     * Sends an event to the remote client
     *
     * @param data The event data
     * @param callback A callback that is notified on Success or failure
     */
    public void send(String data, EventCallback callback) {
        send(data, null, null, callback);
    }

    /**
     * Sends an event to the remote client
     *
     * @param data The event data
     * @param event The event name
     * @param id The event ID
     * @param callback A callback that is notified on Success or failure
     */
    public void send(String data, String event, String id, EventCallback callback) {
        if (open == 0 || shutdown) {
            if (callback != null) {
                callback.failed(this, event, data, id, new ClosedChannelException());
            }
            return;
        }
        queue.add(new SSEData(event, data, id, callback));
        sink.getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                if (pooled == null) {
                    fillBuffer();
                    writeListener.handleEvent(sink);
                }
            }
        });
    }

    public String getParameter(String name) {
        if(parameters == null) {
            return null;
        }
        return parameters.get(name);
    }

    public void setParameter(String name, String value) {
        if(parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(name, value);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     *
     *
     * @return The keep alive time
     */
    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Sets the keep alive time in milliseconds. If this is larger than zero a ':' message will be sent this often
     * (assuming there is no activity) to keep the connection alive.
     *
     * The spec recommends a value of 15000 (15 seconds).
     *
     * @param keepAliveTime The time in milliseconds between keep alive messaged
     */
    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
        if(this.timerKey != null) {
            this.timerKey.remove();
        }
        this.timerKey = sink.getIoThread().executeAtInterval(new Runnable() {
            @Override
            public void run() {
                if(shutdown || open == 0) {
                    if(timerKey != null) {
                        timerKey.remove();
                    }
                    return;
                }
                if(pooled == null) {
                    pooled = exchange.getConnection().getBufferPool().allocate();
                    pooled.getResource().put(":\n".getBytes(StandardCharsets.UTF_8));
                    pooled.getResource().flip();
                    writeListener.handleEvent(sink);
                }
            }
        }, keepAliveTime, TimeUnit.MILLISECONDS);
    }

    private void fillBuffer() {
        if (queue.isEmpty()) {
            if(pooled != null) {
                pooled.free();
                pooled = null;
                sink.suspendWrites();
            }
            return;
        }

        if (pooled == null) {
            pooled = exchange.getConnection().getBufferPool().allocate();
        } else {
            pooled.getResource().clear();
        }
        ByteBuffer buffer = pooled.getResource();

        while (!queue.isEmpty() && buffer.hasRemaining()) {
            SSEData data = queue.poll();
            buffered.add(data);
            if (data.leftOverData == null) {
                StringBuilder message = new StringBuilder();
                if (data.id != null) {
                    message.append("id:");
                    message.append(data.id);
                    message.append('\n');
                }
                if (data.event != null) {
                    message.append("event:");
                    message.append(data.event);
                    message.append('\n');
                }
                if (data.data != null) {
                    message.append("data:");
                    message.append(data.data);
                    message.append('\n');
                }
                message.append('\n');
                byte[] messageBytes = message.toString().getBytes(StandardCharsets.UTF_8);
                if (messageBytes.length < buffer.remaining()) {
                    buffer.put(messageBytes);
                    data.endBufferPosition = buffer.position();
                } else {
                    int rem = buffer.remaining();
                    buffer.put(messageBytes, 0, rem);
                    data.leftOverData = messageBytes;
                    data.leftOverDataOffset = rem;
                }
            } else {
                int remainingData = data.leftOverData.length - data.leftOverDataOffset;
                if (remainingData > buffer.remaining()) {
                    int toWrite = buffer.remaining();
                    buffer.put(data.leftOverData, data.leftOverDataOffset, toWrite);
                    data.leftOverDataOffset += toWrite;
                } else {
                    buffer.put(data.leftOverData, data.leftOverDataOffset, remainingData);
                    data.endBufferPosition = buffer.position();
                    data.leftOverData = null;
                }
            }
        }
        buffer.flip();
        sink.resumeWrites();
    }

    /**
     * execute a graceful shutdown once all data has been sent
     */
    public void shutdown() {
        if (open == 0 || shutdown) {
            return;
        }
        shutdown = true;
        sink.getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                if (queue.isEmpty() && pooled == null) {
                    try {
                        sink.shutdownWrites();
                    } catch (IOException e) {
                        //ignore
                    }
                    IoUtils.safeClose(ServerSentEventConnection.this);
                }
            }
        });
    }

    @Override
    public boolean isOpen() {
        return open != 0;
    }

    @Override
    public void close() throws IOException {
        if (openUpdater.compareAndSet(this, 1, 0)) {
            if (pooled != null) {
                pooled.free();
                pooled = null;
            }
            queue.clear();
            buffered.clear();
            sink.close();
        }
    }

    @Override
    public <T> T getAttachment(AttachmentKey<T> key) {
        return exchange.getAttachment(key);
    }

    @Override
    public <T> List<T> getAttachmentList(AttachmentKey<? extends List<T>> key) {
        return exchange.getAttachmentList(key);
    }

    @Override
    public <T> T putAttachment(AttachmentKey<T> key, T value) {
        return exchange.putAttachment(key, value);
    }

    @Override
    public <T> T removeAttachment(AttachmentKey<T> key) {
        return exchange.removeAttachment(key);
    }

    @Override
    public <T> void addToAttachmentList(AttachmentKey<AttachmentList<T>> key, T value) {
        exchange.addToAttachmentList(key, value);
    }

    public interface EventCallback {

        void done(ServerSentEventConnection connection, String data, String event, String id);

        void failed(ServerSentEventConnection connection, String data, String event, String id, IOException e);

    }

    private static class SSEData {
        final String event;
        final String data;
        final String id;
        final EventCallback callback;
        private int endBufferPosition = -1;
        private byte[] leftOverData;
        private int leftOverDataOffset;

        private SSEData(String event, String data, String id, EventCallback callback) {
            this.event = event;
            this.data = data;
            this.id = id;
            this.callback = callback;
        }


    }

    private class SseWriteListener implements ChannelListener<StreamSinkChannel> {
        @Override
        public void handleEvent(StreamSinkChannel channel) {
            if (pooled == null) {
                channel.suspendWrites();
                return;
            }
            try {
                ByteBuffer buffer = pooled.getResource();
                int res;
                do {
                    res = channel.write(buffer);
                    Iterator<SSEData> itr = buffered.iterator();
                    while (itr.hasNext()) {
                        //figure out which messages are complete
                        SSEData data = itr.next();
                        if (data.endBufferPosition > 0 && buffer.position() >= data.endBufferPosition) {
                            if(data.callback != null) {
                                data.callback.done(ServerSentEventConnection.this, data.data, data.event, data.id);
                            }
                            itr.remove();
                        } else {
                            break;
                        }
                    }
                    if (res == 0) {
                        sink.resumeWrites();
                        return;
                    } else if (!buffer.hasRemaining()) {
                        fillBuffer();
                    }
                } while (res > 0);
            } catch (IOException e) {
                handleException(e);
            }
        }
    }

    private void handleException(IOException e) {
        for (SSEData i : buffered) {
            if(i.callback != null) {
                i.callback.failed(this, i.data, i.event, i.id, e);
            }
        }
        for(SSEData i : queue) {
            if(i.callback != null) {
                i.callback.failed(this, i.data, i.event, i.id, e);
            }
        }
        IoUtils.safeClose(this);
    }
}
