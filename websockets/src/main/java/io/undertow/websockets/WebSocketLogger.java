/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package io.undertow.websockets;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Cause;
import org.jboss.logging.LogMessage;
import org.jboss.logging.Logger;
import org.jboss.logging.Message;
import org.jboss.logging.MessageLogger;

/**
 * log messages start at 25000
 *
 * @author Stuart Douglas
 */
@MessageLogger(projectCode = "UNDERTOW")
public interface WebSocketLogger extends BasicLogger {

    WebSocketLogger ROOT_LOGGER = Logger.getMessageLogger(WebSocketLogger.class, WebSocketLogger.class.getPackage().getName());

    WebSocketLogger REQUEST_LOGGER = Logger.getMessageLogger(WebSocketLogger.class, WebSocketLogger.class.getPackage().getName() + ".request");

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 25001, value = "WebSocket handshake failed")
    void webSocketHandshakeFailed(@Cause Throwable cause);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 25002, value = "StreamSinkFrameChannel %s was closed before writing was finished, web socket connection is now unusable")
    void closedBeforeFinishedWriting(StreamSinkFrameChannel streamSinkFrameChannel);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 25003, value = "Decoding WebSocket Frame with opCode %s")
    void decodingFrameWithOpCode(int opCode);
}
