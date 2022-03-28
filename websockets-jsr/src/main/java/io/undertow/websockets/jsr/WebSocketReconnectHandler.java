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

package io.undertow.websockets.jsr;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import java.io.IOException;
import java.net.URI;

/**
 * A reconnect handler for web socket connections. If a websocket is reconnected it will re-use the same web socket
 * endpoint instance.
 *
 * Note that only a single reconnect handler instance can be registered for each deployment. If a reconnect handler
 * wishes to save state it should store it in the session attributes
 *
 * @author Stuart Douglas
 */
public interface WebSocketReconnectHandler {

    /**
     * Method that is invoked by the reconnect handler after disconnection
     *
     * @param closeReason The close reason
     * @return The number of milliseconds to wait for a reconnect, or -1 if no reconnect should be attempted
     */
    long disconnected(CloseReason closeReason, URI connectionUri, Session session, int disconnectCount);


    /**
     * Method that is invoked if the reconnection fails
     *
     * @param exception The failure exception
     * @return The number of milliseconds to wait for a reconnect, or -1 if no reconnect should be attempted
     */
    long reconnectFailed(IOException exception, URI connectionUri, Session session, int failedCount);

}

