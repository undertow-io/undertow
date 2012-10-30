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

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.jboss.logging.Message;
import org.jboss.logging.MessageBundle;
import org.jboss.logging.Messages;

/**
 * start at 20000
 * @author Stuart Douglas
 */
@MessageBundle(projectCode = "UNDERTOW")
public interface WebSocketMessages {

    WebSocketMessages MESSAGES = Messages.getBundle(WebSocketMessages.class);

    @Message(id = 2001, value = "Not a WebSocket handshake request: missing %s in the headers")
    WebSocketHandshakeException missingHeader(final String header);

    @Message(id = 2002, value = "Channel is closed")
    IOException channelClosed();

    @Message(id = 2003, value = "Text frame contains non UTF-8 data")
    UnsupportedEncodingException invalidTextFrameEncoding();

    @Message(id = 2004, value = "Cannot call shutdownWrites, only %s of %s bytes written")
    IOException notAllPayloadDataWritten(long written, long payloadSize);

    @Message(id = 2005, value = "Fragmented control frame")
    WebSocketFrameCorruptedException fragmentedControlFrame();

    @Message(id = 2006, value = "Control frame with payload length > 125 octets")
    WebSocketFrameCorruptedException toBigControlFrame();

    @Message(id = 2007, value = "Control frame using reserved opcode = %s")
    WebSocketFrameCorruptedException reservedOpCodeInControlFrame(int opCode);

    @Message(id = 2008, value = "Received close control frame with payload len 1")
    WebSocketFrameCorruptedException controlFrameWithPayloadLen1();

    @Message(id = 2009, value = "Data frame using reserved opcode = %s")
    WebSocketFrameCorruptedException reservedOpCodeInDataFrame(int opCode);

    @Message(id = 2010, value = "Received continuation data frame outside fragmented message")
    WebSocketFrameCorruptedException continuationFrameOutsideFragmented();

    @Message(id = 2011, value = "Received non-continuation data frame while inside fragmented message")
    WebSocketFrameCorruptedException nonContinuationFrameInsideFragmented();

    @Message(id = 2012, value = "Invalid data frame length (not using minimal length encoding)")
    WebSocketFrameCorruptedException invalidDataFrameLength();

    @Message(id = 2013, value = "Cannot decode web socket frame with opcode: %s")
    WebSocketFrameCorruptedException unsupportedOpCode(int opCode);

    @Message(id = 2014, value = "WebSocketFrameType %s is not supported by this WebSocketChannel\"")
    IllegalArgumentException unsupportedFrameType(WebSocketFrameType type);

    @Message(id = 2015, value = "Extensions not allowed but received rsv of %s")
    WebSocketFrameCorruptedException extensionsNotAllowed(int rsv);



}
