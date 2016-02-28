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

package io.undertow.util;

import io.undertow.UndertowLogger;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class ConnectionUtils {

    private ConnectionUtils() {

    }

    /**
     * Cleanly close a connection, by shutting down and flushing writes.
     * <p/>
     * If this fails the connection is forcibly closed.
     *
     * @param connection The connection
     * @param additional Any additional resources to close once the connection has been closed
     */
    public static void cleanClose(final StreamConnection connection, final Closeable... additional) {
        try {
            connection.getSinkChannel().shutdownWrites();
            if (!connection.getSinkChannel().flush()) {
                connection.getSinkChannel().setWriteListener(ChannelListeners.flushingChannelListener(new ChannelListener<ConduitStreamSinkChannel>() {
                    @Override
                    public void handleEvent(ConduitStreamSinkChannel channel) {
                        IoUtils.safeClose(connection);
                        IoUtils.safeClose(additional);
                    }
                }, new ChannelExceptionHandler<ConduitStreamSinkChannel>() {
                    @Override
                    public void handleException(ConduitStreamSinkChannel channel, IOException exception) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                        IoUtils.safeClose(connection);
                        IoUtils.safeClose(additional);
                    }
                }));
                connection.getSinkChannel().resumeWrites();
            } else {
                IoUtils.safeClose(connection);
                IoUtils.safeClose(additional);
            }

        } catch (Exception e) {
            if (e instanceof IOException) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException((IOException) e);
            } else {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(e));
            }
            IoUtils.safeClose(connection);
            IoUtils.safeClose(additional);
        }
    }
}
