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

package io.undertow.io;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Sender interface that allows for callback based async IO.
 * <p>
 * Note that all methods on this class are asynchrono˜us, and may result in dispatch to an IO thread. After calling
 * a method on this class you should not perform any more work on the current exchange until the callback is invoked.
 * <p>
 * NOTE: implementers of this interface should be careful that they do not recursively call onComplete, which can
 * lead to stack overflows if send is called many times.
 *
 * @author Stuart Douglas
 */
public interface Sender {

    /**
     * Write the given buffer using async IO, and calls the given callback on completion or error.
     *
     * @param buffer   The buffer to send.
     * @param callback The callback
     */
    void send(final ByteBuf buffer, final IoCallback<Sender> callback);

    /**
     * Write the given buffers using async IO, and calls the given callback on completion or error.
     *
     * @param buffer   The buffers to send.
     * @param callback The callback
     */
    default void send(final ByteBuf[] buffer, final IoCallback<Sender> callback) {
        send(Unpooled.wrappedBuffer(buffer), callback);
    }

    /**
     * Write the given buffer using async IO, and ends the exchange when done
     *
     * @param buffer The buffer to send.
     */
    default void send(final ByteBuf buffer) {
        send(buffer, IoCallback.END_EXCHANGE);
    }

    /**
     * Write the given buffers using async IO, and ends the exchange when done
     *
     * @param buffer The buffers to send.
     */
    default void send(final ByteBuf[] buffer) {
        send(buffer, IoCallback.END_EXCHANGE);
    }

    /**
     * Write the given String using async IO, and calls the given callback on completion or error.
     * <p>
     * The CharSequence is encoded to UTF8
     *
     * @param data     The data to send
     * @param callback The callback
     */
    default void send(final String data, final IoCallback<Sender> callback){
        send(data, StandardCharsets.UTF_8, callback);
    }

    /**
     * Write the given String using async IO, and calls the given callback on completion or error.
     *
     * @param data     The buffer to end.
     * @param charset  The charset to use
     * @param callback The callback
     */
    void send(final String data, final Charset charset, final IoCallback<Sender> callback);


    /**
     * Write the given String using async IO, and ends the exchange when done
     * <p>
     * The CharSequence is encoded to UTF8
     *
     * @param data The data to send
     */
    default void send(final String data) {
        send(data, StandardCharsets.UTF_8, IoCallback.END_EXCHANGE);
    }

    /**
     * Write the given String using async IO, and ends the exchange when done
     *
     * @param data    The buffer to end.
     * @param charset The charset to use
     */
    default void send(final String data, final Charset charset) {
        send(data, charset, IoCallback.END_EXCHANGE);
    }


    /**
     * Transfers all content from the specified file
     *
     * @param channel  the file channel to transfer
     * @param callback The callback
     */
    void transferFrom(final RandomAccessFile channel, final IoCallback<Sender> callback);

    /**
     * Transfers all content from the specified file
     *
     * @param channel  the file channel to transfer
     * @param callback The callback
     */
    void transferFrom(final RandomAccessFile channel, long start, long length, final IoCallback<Sender> callback);
    /**
     * Closes this sender asynchronously. The given callback is notified on completion
     *
     * @param callback The callback that is notified when all data has been flushed and the channel is closed
     */
    void close(final IoCallback<Sender> callback);

    /**
     * Closes this sender asynchronously
     */
    default void close() {
        close(IoCallback.END_EXCHANGE);
    }
}
