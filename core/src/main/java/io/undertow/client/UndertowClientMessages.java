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

import io.undertow.util.HttpString;
import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

import java.io.IOException;
import java.net.URI;

/**
 * starting from 1000
 *
 * @author Emanuel Muckenhuber
 */
@MessageBundle(projectCode = "UT")
public interface UndertowClientMessages {

    UndertowClientMessages MESSAGES = Messages.getBundle(UndertowClientMessages.class);

    // 1000
    @Message(id = 1000, value = "Connection closed")
    String connectionClosed();

    @Message(id = 1001, value = "Request already written")
    IllegalStateException requestAlreadyWritten();

    // 1020
    @Message(id = 1020, value = "Failed to upgrade channel due to response %s (%s)")
    String failedToUpgradeChannel(final int responseCode, String reason);

    // 1030
    @Message(id = 1030, value = "invalid content length %d")
    IllegalArgumentException illegalContentLength(long length);

    @Message(id = 1031, value = "Unknown scheme in URI %s")
    IllegalArgumentException unknownScheme(URI uri);

    @Message(id = 1032, value = "Unknown transfer encoding %s")
    IOException unknownTransferEncoding(String transferEncodingString);

    @Message(id = 1033, value = "Invalid connection state")
    IOException invalidConnectionState();

    @Message(id = 1034, value = "Unknown AJP packet type %s")
    IOException unknownAjpMessageType(byte packetType);

    @Message(id = 1035, value = "Unknown method type for AJP request %s")
    IOException unknownMethod(HttpString method);

    @Message(id = 1036, value = "Data still remaining in chunk %s")
    IOException dataStillRemainingInChunk(long remaining);

    @Message(id = 1037, value = "Wrong magic number, expected %s, actual %s")
    IOException wrongMagicNumber(String expected, String actual);

    @Message(id = 1038, value = "Received invalid AJP chunk %s with response already complete")
    IOException receivedInvalidChunk(byte prefix);
}
