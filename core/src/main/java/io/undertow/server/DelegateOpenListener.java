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

package io.undertow.server;

import io.undertow.connector.PooledByteBuffer;
import org.xnio.StreamConnection;

/**
 * An open listener that handles being delegated to, e.g. by NPN or ALPN
 *
 * @author Stuart Douglas
 */
public interface DelegateOpenListener extends OpenListener {

    /**
     *
     * @param channel The channel
     * @param additionalData Any additional data that was read from the stream as part of the handshake process
     */
    void handleEvent(final StreamConnection channel, PooledByteBuffer additionalData);
}
