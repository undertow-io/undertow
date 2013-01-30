/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
package io.undertow.server.handlers.file;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.undertow.server.HttpServerExchange;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;

/**
 * Utility class to assist non-blocking handlers with transferring buffers. An attempt is made to write
 * the buffers immediately. If the buffers do not complete, they are saved and a write listener is registered
 * to finish the transfer.
 *
* @author Jason T. Greene
*/
public class BufferTransfer implements ChannelListener<StreamSinkChannel> {
    private final ByteBuffer[] buffers;
    private final boolean recurse;
    private final TransferCompletionCallback callback;

    private final HttpServerExchange exchange;

    public interface TransferCompletionCallback {
        void complete();
    }

    public static void transfer(HttpServerExchange exchange, StreamSinkChannel channel, TransferCompletionCallback callback, ByteBuffer[] buffers) {
        new BufferTransfer(callback, exchange, buffers, true).handleEvent(channel);
    }

    private BufferTransfer(TransferCompletionCallback callback, HttpServerExchange exchange, ByteBuffer[] buffers, boolean recurse) {
        this.callback = callback;
        this.buffers = buffers;
        this.recurse = recurse;
        this.exchange = exchange;
    }

    public void handleEvent(final StreamSinkChannel channel) {
        boolean complete = true;
        try {
            ByteBuffer last = buffers[buffers.length - 1];
            while (last.remaining() > 0) {
                long res;
                try {
                    res = channel.write(buffers);
                } catch (IOException e) {
                    IoUtils.safeClose(channel);
                    exchange.setResponseCode(500);
                    exchange.endExchange();
                    return;
                }

                if (res == 0L) {
                    if (recurse) {
                        channel.getWriteSetter().set(new BufferTransfer(callback, exchange, buffers, false));
                        channel.resumeWrites();
                    }
                    complete = false; // Entry still in-use
                    return;
                }
            }
        } finally {
            if (complete && callback != null) {
                callback.complete();
            }
        }
        exchange.endExchange();
    }
}
