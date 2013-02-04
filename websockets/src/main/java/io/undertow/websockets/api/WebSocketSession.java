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

import java.util.Set;

/**
 * Session for a WebSocket connection. For each new connection a {@link WebSocketSession} will be created.
 * This {@link WebSocketSession} can then be used to communicate with the remote peer.
 * <p/>
 * Implementations of the interface are expected to be thread-safe, however if multiple threads
 * are sending messages no guarantees are provided about the resulting message order.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketSession extends BinaryFrameSender, TextFrameSender, PingFrameSender, PongFrameSender, CloseFrameSender {
    /**
     * Unique id for the session
     */
    String getId();

    /**
     * Return a {@link FragmentedBinaryFrameSender} which can be used to send a binary frame in chunks.
     *
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    FragmentedBinaryFrameSender sendFragmentedBinary();

    /**
     * Return a {@link FragmentedTextFrameSender} which can be used to send a text frame in chunks.
     *
     * @throws IllegalStateException
     *          Is thrown if a {@link FragmentedSender} is still in use.
     */
    FragmentedTextFrameSender sendFragmentedText();


    /**
     * Set a attribute on the session. When the value is {@code null} it will remove the attribute with the key.
     */
    boolean setAttribute(String key, Object value);

    /**
     * Return the attribute for the key or {@code null} if non is stored for the key
     */
    Object getAttribute(String key);

    /**
     * Return {@code true} if this is a secure websocket connection
     */
    boolean isSecure();

    /**
     * Return the path for which the session was established
     */
    String getPath();

    /**
     * Set the {@link FrameHandler} which is used for all frames. If non is set all frames will
     * just be discarded. Returns the {@link FrameHandler} which was set before.
     * <p/>
     * Be aware that if you set a new {@link FrameHandler} it will only be used for the next websocket
     * frame. In progress handling of a frame will continue with the old one.
     */
    FrameHandler setFrameHandler(FrameHandler handler);

    /**
     *
     * @return The current frame handler
     */
    FrameHandler getFrameHandler();

    /**
     * Return an unmodifiable {@link Set} of sub-protocols for which the {@link WebSocketSession} will be used. May
     * return an empty {@link Set}
     */
    Set<String> getSubProtocols();

    /**
     * Set the idle timeout for this {@link WebSocketSession}. The session will be closed
     * if nothing was received or send in this time.
     *
     * @param idleTimeout   the idle timeout in ms. If the smaller then 1 no timeout is used.
     */
    void setIdleTimeout(int idleTimeout);

    /**
     * Get the idle timeout for this {@link WebSocketSession}. The session will be closed
     * if nothing was received or send in this time.
     *
     * @return the idle timeout in ms. If the smaller then 1 no timeout is used.
     */
    int getIdleTimeout();

    /**
     * Set the send timeout for this {@link WebSocketSession} when sending a Websocket frame in an async fashion
     * The session will be closed if the send did not complete in the specified timeout.
     *
     * @param asyncSendTimeout   the async send timeout in ms. If the smaller then 1 no timeout is used.
     */
    void setAsyncSendTimeout(int asyncSendTimeout);

    /**
     * Get the send timeout for this {@link WebSocketSession} when sending a Websocket frame in an async fashion
     * The session will be closed if the send did not complete in the specified timeout.
     *
     * @return the async send timeout in ms. If the smaller then 1 no timeout is used.
     */
    int getAsyncSendTimeout();

    /**
     * Set the max frame size in bytes this {@link WebSocketSession} can handle while receive TEXT and BINARY frames.
     *
     * @param size  the max size in bytes or &lt;1 if no limit is in place.
     */
    void setMaximumFrameSize(long size);

    /**
     * Get the max frame size in bytes this {@link WebSocketSession} can handle while receive TEXT and BINARY frames.
     *
     * @return size  the max size in bytes or &lt;1 if no limit is in place.
     *
     */
    long getMaximumFrameSize();
}
