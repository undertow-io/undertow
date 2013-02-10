package io.undertow.io;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Sender interface that allows for callback based async IO.
 *
 * Note that all methods on this class are asynchronous, and may result in dispatch to an IO thread. After calling
 * a method on this class you should not perform any more work on the current exchange until the callback is invoked.
 *
 *
 *
 * TODO: Look at more closely aligning this with the web socket senders
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
     * Write the given String using async IO, and calls the given callback on completion or error.
     * <p/>
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
