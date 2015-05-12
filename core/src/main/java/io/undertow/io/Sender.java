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

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * Sender interface that allows for callback based async IO.
 *
 * Note that all methods on this class are asynchronous, and may result in dispatch to an IO thread. After calling
 * a method on this class you should not perform any more work on the current exchange until the callback is invoked.
 *
 * NOTE: implementers of this interface should be careful that they do not recursively call onComplete, which can
 * lead to stack overflows if send is called many times.
 *
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
    void send(final ByteBuffer buffer, final IoCallback callback);

    /**
     * Write the given buffers using async IO, and calls the given callback on completion or error.
     *
     * @param buffer   The buffers to send.
     * @param callback The callback
     */
    void send(final ByteBuffer[] buffer, final IoCallback callback);

    /**
     * Write the given buffer using async IO, and ends the exchange when done
     *
     * @param buffer   The buffer to send.
     */
    void send(final ByteBuffer buffer);

    /**
     * Write the given buffers using async IO, and ends the exchange when done
     *
     * @param buffer   The buffers to send.
     */
    void send(final ByteBuffer[] buffer);

    /**
     * Write the given String using async IO, and calls the given callback on completion or error.
     * <p>
     * The CharSequence is encoded to UTF8
     *
     * @param data     The data to send
     * @param callback The callback
     */
    void send(final String data, final IoCallback callback);

    /**
     * Write the given String using async IO, and calls the given callback on completion or error.
     *
     * @param data     The buffer to end.
     * @param charset  The charset to use
     * @param callback The callback
     */
    void send(final String data, final Charset charset, final IoCallback callback);


    /**
     * Write the given String using async IO, and ends the exchange when done
     * <p>
     * The CharSequence is encoded to UTF8
     *
     * @param data     The data to send
     */
    void send(final String data);

    /**
     * Write the given String using async IO, and ends the exchange when done
     *
     * @param data     The buffer to end.
     * @param charset  The charset to use
     */
    void send(final String data, final Charset charset);


    /**
     * Transfers all content from the specified file
     *
     * @param channel the file channel to transfer
     * @param callback The callback
     */
    void transferFrom(final FileChannel channel, final IoCallback callback);

    /**
     * Closes this sender asynchronously. The given callback is notified on completion
     *
     * @param callback The callback that is notified when all data has been flushed and the channel is closed
     */
    void close(final IoCallback callback);

    /**
     * Closes this sender asynchronously
     *
     */
    void close();
}
