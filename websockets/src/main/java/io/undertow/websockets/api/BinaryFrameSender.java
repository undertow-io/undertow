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
package io.undertow.websockets.api;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Implementations can be used to send BINARY frames.
 *
 * @author Stuart Douglas
 */
public interface BinaryFrameSender {

    /**
     * Send the a binary websocket frame and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload
     * @param callback
     *          The callback that is called when sending is done or {@code null} if no notification
     *          should be done.
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    void sendBinary(ByteBuffer payload, SendCallback callback);

    /**
     * Send the a binary websocket frame and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload
     * @param callback
     *          The callback that is called when sending is done or {@code null} if no notification
     *          should be done.
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    void sendBinary(ByteBuffer[] payload, SendCallback callback);

    /**
     * Send the a binary websocket frame and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payloadChannel
     *          The {@link FileChannel} which is used as the source of the payload
     * @param offset
     *          the offset which is used as starting point in the {@link FileChannel}
     * @param length
     *          the number of bytes to transfer
     * @param callback
     *          The callback that is called when sending is done or {@code null} if no notification
     *          should be done.
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    void sendBinary(FileChannel payloadChannel, int offset, long length, SendCallback callback);

    /**
     * Send the a binary websocket frame and blocks until complete.
     * <p/>
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload
     * @throws IOException
     *          If sending failed
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    void sendBinary(ByteBuffer payload) throws IOException;

    /**
     * Send the a binary websocket frame and blocks until complete.
     * <p/>
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload
     * @throws IOException
     *          If sending failed
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    void sendBinary(ByteBuffer[] payload) throws IOException;

    /**
     * Sends a binary message using the resulting output stream.
     * <p/>
     * This methods will block until the implementation is ready to actually send the message
     * (i.e. all previous messages in the queue have been sent).
     *
     * @param payloadSize
     *          The payload size
     * @return stream
     *          A stream that can be used to send a binary message
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    OutputStream sendBinary(long payloadSize) throws IOException;
}
