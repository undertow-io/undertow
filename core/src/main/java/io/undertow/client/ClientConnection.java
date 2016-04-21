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

package io.undertow.client;

import org.xnio.ChannelListener;
import org.xnio.Option;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Channel;

/**
 * A client connection. This can be used to send requests, or to upgrade the connection.
 * <p>
 * In general these objects are not thread safe, they should only be used by the IO thread
 * that is responsible for the connection. As a result this client does not provide a mechanism
 * to perform blocking IO, it is designed for async operation only.
 *
 * @author Stuart Douglas
 */
public interface ClientConnection extends Channel {

    /**
     * Sends a client request. The request object should not be modified after it has been submitted to the connection.
     * <p>
     * Request objects can be queued. Once the request is in a state that it is ready to be sent the {@code clientCallback}
     * is invoked to provide the caller with the {@link ClientExchange}
     * <p>
     * If {@link #isMultiplexingSupported()} returns true then multiple requests may be active at the same time, and a later
     * request may complete before an earlier one.
     * <p>
     * Note that the request header may not be written out until after the callback has been invoked. This allows the
     * client to write out a header with a gathering write if the request contains content.
     *
     * @param request The request to send.
     */
    void sendRequest(final ClientRequest request, final ClientCallback<ClientExchange> clientCallback);

    /**
     * Upgrade the connection, if the underlying protocol supports it. This should only be called after an upgrade request
     * has been submitted and the target server has accepted the upgrade.
     *
     * @return The resulting StreamConnection
     */
    StreamConnection performUpgrade() throws IOException;

    /**
     *
     * @return The buffer pool used by the client
     */
    ByteBufferPool getBufferPool();

    SocketAddress getPeerAddress();

    <A extends SocketAddress> A getPeerAddress(Class<A> type);

    ChannelListener.Setter<? extends ClientConnection> getCloseSetter();

    SocketAddress getLocalAddress();

    <A extends SocketAddress> A getLocalAddress(Class<A> type);

    XnioWorker getWorker();

    XnioIoThread getIoThread();

    boolean isOpen();

    boolean supportsOption(Option<?> option);

    <T> T getOption(Option<T> option) throws IOException;

    <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException;

    boolean isUpgraded();

    /**
     *
     * @return <code>true</code> if this connection support server push
     */
    boolean isPushSupported();

    /**
     *
     * @return <code>true</code> if this client supports multiplexing
     */
    boolean isMultiplexingSupported();

    /**
     *
     * @return the statistics information, or <code>null</code> if statistics are not supported or disabled
     */
    ClientStatistics getStatistics();

    boolean isUpgradeSupported();

    /**
     * Adds a close listener, than will be invoked with the connection is closed
     *
     * @param listener The close listener
     */
    void addCloseListener(ChannelListener<ClientConnection> listener);
}
