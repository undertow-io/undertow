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

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Channel;
import org.xnio.ChannelExceptionHandler;
import org.xnio.IoUtils;

import io.undertow.UndertowLogger;

/**
 *
 * Channel exception handler that closes the channel, logs a debug level
 * message and closes arbitrary other resources.
 *
 * @author Stuart Douglas
 */
public class ClosingChannelExceptionHandler<T extends Channel> implements ChannelExceptionHandler<T> {

    private final Closeable[] closable;

    public ClosingChannelExceptionHandler(Closeable... closable) {
        this.closable = closable;
    }

    @Override
    public void handleException(T t, IOException e) {
        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
        IoUtils.safeClose(t);
        IoUtils.safeClose(closable);
    }
}
