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

package io.undertow.websockets.core;

import io.undertow.websockets.WebSocketExtension;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * log messages start at 25000
 *
 * @author Stuart Douglas
 */
@MessageLogger(projectCode = "UT")
public interface WebSocketLogger extends BasicLogger {

    WebSocketLogger ROOT_LOGGER = Logger.getMessageLogger(WebSocketLogger.class, WebSocketLogger.class.getPackage().getName());

    WebSocketLogger REQUEST_LOGGER = Logger.getMessageLogger(WebSocketLogger.class, WebSocketLogger.class.getPackage().getName() + ".request");

    WebSocketLogger EXTENSION_LOGGER = Logger.getMessageLogger(WebSocketLogger.class, WebSocketLogger.class.getPackage().getName() + ".extension");
//
//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 25001, value = "WebSocket handshake failed")
//    void webSocketHandshakeFailed(@Cause Throwable cause);
//
//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 25002, value = "StreamSinkFrameChannel %s was closed before writing was finished, web socket connection is now unusable")
//    void closedBeforeFinishedWriting(StreamSinkFrameChannel streamSinkFrameChannel);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 25003, value = "Decoding WebSocket Frame with opCode %s")
    void decodingFrameWithOpCode(int opCode);
//
//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 25004, value = "Failure during execution of SendCallback")
//    void sendCallbackExecutionError(@Cause Throwable cause);
//
//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 25005, value = "Failed to set idle timeout")
//    void setIdleTimeFailed(@Cause Throwable cause);
//
//
//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 25006, value = "Failed to get idle timeout")
//    void getIdleTimeFailed(@Cause Throwable cause);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 25007, value = "Unhandled exception for annotated endpoint %s")
    void unhandledErrorInAnnotatedEndpoint(Object instance, @Cause Throwable thr);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 25008, value = "Incorrect parameter %s for extension")
    void incorrectExtensionParameter(WebSocketExtension.Parameter param);
}
