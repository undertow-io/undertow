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

import javax.websocket.CloseReason;
import java.net.URI;

/**
 * Interface that can be used to listen for web socket disconnect and connection
 * failure events, and handle the reconnection. Both methods return a long timeout,
 * which is the number of milliseconds to wait before attempting reconnect.
 *
 * These entries are loaded from META-INF/services entries on deployment time.
 *
 * @author Stuart Douglas
 */
public interface WebsocketReconnectHandler {

    long onConnectionClose(CloseReason closeReason, URI connectedUri, Object endpoint);

    long onConnectionFailure(Exception exception, URI connectionURI);

}
