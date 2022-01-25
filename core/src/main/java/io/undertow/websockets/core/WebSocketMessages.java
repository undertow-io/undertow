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

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.zip.DataFormatException;

/**
 * start at 20000
 * @author Stuart Douglas
 */
@MessageBundle(projectCode = "UT")
public interface WebSocketMessages {

    WebSocketMessages MESSAGES = Messages.getBundle(WebSocketMessages.class);
//
//    @Message(id = 2001, value = "Not a WebSocket handshake request: missing %s in the headers")
//    WebSocketHandshakeException missingHeader(String header);

    @Message(id = 2002, value = "Channel is closed")
    IOException channelClosed();

    @Message(id = 2003, value = "Text frame contains non UTF-8 data")
    UnsupportedEncodingException invalidTextFrameEncoding();
//
//    @Message(id = 2004, value = "Cannot call shutdownWrites, only %s of %s bytes written")
//    IOException notAllPayloadDataWritten(long written, long payloadSize);

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
//
//    @Message(id = 2012, value = "Invalid data frame length (not using minimal length encoding)")
//    WebSocketFrameCorruptedException invalidDataFrameLength();

    @Message(id = 2013, value = "Cannot decode web socket frame with opcode: %s")
    IllegalStateException unsupportedOpCode(int opCode);

    @Message(id = 2014, value = "WebSocketFrameType %s is not supported by this WebSocketChannel\"")
    IllegalArgumentException unsupportedFrameType(WebSocketFrameType type);

    @Message(id = 2015, value = "Extensions not allowed but received rsv of %s")
    WebSocketFrameCorruptedException extensionsNotAllowed(int rsv);

    @Message(id = 2016, value = "Could not find supported protocol in request list %s. Supported protocols are %s")
    WebSocketHandshakeException unsupportedProtocol(String requestedSubprotocols, Collection<String> subprotocols);
//
//    @Message(id = 2017, value = "No Length encoded in the frame")
//    WebSocketFrameCorruptedException noLengthEncodedInFrame();
//
//    @Message(id = 2018, value = "Payload is not support in CloseFrames when using WebSocket Version 00")
//    IllegalArgumentException payloadNotSupportedInCloseFrames();

    @Message(id = 2019, value = "Invalid payload for PING (payload length must be <= 125, was %s)")
    IllegalArgumentException invalidPayloadLengthForPing(long payloadLength);
//
//    @Message(id = 2020, value = "Payload is not supported for Close Frames when using WebSocket 00")
//    IOException noPayloadAllowedForCloseFrames();
//
//    @Message(id = 2021, value = "Fragmentation not supported")
//    UnsupportedOperationException fragmentationNotSupported();
//
//    @Message(id = 2022, value = "Can only be changed before the write is in progress")
//    IllegalStateException writeInProgress();

    @Message(id = 2023, value = "Extensions not supported")
    UnsupportedOperationException extensionsNotSupported();
//
//    @Message(id = 2024, value = "The payload length must be >= 0")
//    IllegalArgumentException negativePayloadLength();
//
//    @Message(id = 2025, value = "Closed before all bytes where read")
//    IOException closedBeforeAllBytesWereRead();

    @Message(id = 2026, value = "Invalid close frame status code: %s")
    WebSocketInvalidCloseCodeException invalidCloseFrameStatusCode(int statusCode);

    @Message(id = 2027, value = "Could not send data, as the underlying web socket connection has been broken")
    IOException streamIsBroken();
//
//    @Message(id = 2028, value = "Specified length is bigger the available size of the FileChannel")
//    IllegalArgumentException lengthBiggerThenFileChannel();
//
//    @Message(id = 2029, value = "FragmentedSender was complete already")
//    IllegalArgumentException fragmentedSenderCompleteAlready();
//
//    @Message(id = 2030, value = "Array of SenderCallbacks must be non empty")
//    IllegalArgumentException senderCallbacksEmpty();
//
//    @Message(id = 2031, value = "Only one FragmentedSender can be used at the same time")
//    IllegalStateException fragmentedSenderInUse();
//
//    @Message(id = 2032, value = "Close frame was send before")
//    IOException closeFrameSentBefore();
//
//    @Message(id = 2033, value = "Blocking operation was called in IO thread")
//    IllegalStateException blockingOperationInIoThread();

    @Message(id = 2034, value = "Web socket frame was not masked")
    WebSocketFrameCorruptedException frameNotMasked();

    @Message(id = 2035, value = "The response did not contain an 'Upgrade: websocket' header")
    IOException noWebSocketUpgradeHeader();

    @Message(id = 2036, value = "The response did not contain a 'Connection: upgrade' header")
    IOException noWebSocketConnectionHeader();

    @Message(id = 2037, value = "Sec-WebSocket-Accept mismatch, expecting %s, received %s")
    IOException webSocketAcceptKeyMismatch(String dKey, String acceptKey);
//
//    @Message(id = 2038, value = "Cannot call method with frame type %s, only text or binary is allowed")
//    IllegalArgumentException incorrectFrameType(WebSocketFrameType type);
//
//    @Message(id = 2039, value = "Data has already been released")
//    IllegalStateException dataHasBeenReleased();

    @Message(id = 2040, value = "Message exceeded max message size of %s")
    String messageToBig(long maxMessageSize);
//
//    @Message(id = 2041, value = "Attempted to write more data than the specified payload length")
//    IOException messageOverflow();
//
//    @Message(id = 2042, value = "Server responded with unsupported extension %s. Supported extensions: %s")
//    IOException unsupportedExtension(String part, List<WebSocketExtension> supportedExtensions);
//
//    @Message(id = 2043, value = "WebSocket client is trying to use extensions but there is not extensions configured")
//    IllegalStateException badExtensionsConfiguredInClient();

    @Message(id = 2044, value = "Compressed message payload is corrupted")
    IOException badCompressedPayload(@Cause final DataFormatException cause);

    @Message(id = 2045, value = "Unable to send on newly created channel!")
    IllegalStateException unableToSendOnNewChannel();

    @Message(id = 2046, value = "Closing WebSocket, peer went away.")
    String messageCloseWebSocket();
}
