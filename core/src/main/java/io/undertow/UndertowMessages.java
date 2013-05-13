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

package io.undertow;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import io.undertow.predicate.PredicateBuilder;
import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

/**
 * @author Stuart Douglas
 */
@MessageBundle(projectCode = "UT")
public interface UndertowMessages {

    UndertowMessages MESSAGES = Messages.getBundle(UndertowMessages.class);

    @Message(id = 1, value = "Maximum concurrent requests must be larger than zero.")
    IllegalArgumentException maximumConcurrentRequestsMustBeLargerThanZero();

    @Message(id = 2, value = "The response has already been started")
    IllegalStateException responseAlreadyStarted();

    // id = 3

    @Message(id = 4, value = "getResponseChannel() has already been called")
    IllegalStateException responseChannelAlreadyProvided();

    @Message(id = 5, value = "getRequestChannel() has already been called")
    IllegalStateException requestChannelAlreadyProvided();

    // id = 6

    // id = 7

    @Message(id = 8, value = "Handler cannot be null")
    IllegalArgumentException handlerCannotBeNull();

    @Message(id = 9, value = "Path must be specified")
    IllegalArgumentException pathMustBeSpecified();

    @Message(id = 10, value = "Session not found %s")
    IllegalStateException sessionNotFound(final String session);

    @Message(id = 11, value = "Session manager must not be null")
    IllegalStateException sessionManagerMustNotBeNull();

    @Message(id = 12, value = "Session manager was not attached to the request. Make sure that the SessionAttachmentHander"
            + "is installed in the handler chain")
    IllegalStateException sessionManagerNotFound();

    @Message(id = 13, value = "Argument %s cannot be null")
    IllegalArgumentException argumentCannotBeNull(final String argument);

    @Message(id = 14, value = "close() called with data still to be flushed. Please call shutdownWrites() and then call flush() until it returns true before calling close()")
    IOException closeCalledWithDataStillToBeFlushed();

    @Message(id = 16, value = "Could not add cookie as cookie handler was not present in the handler chain")
    IllegalStateException cookieHandlerNotPresent();

    @Message(id = 17, value = "Form value is a file, use getFile() instead")
    IllegalStateException formValueIsAFile();

    @Message(id = 18, value = "Form value is a String, use getValue() instead")
    IllegalStateException formValueIsAString();

    @Message(id = 19, value = "Connection from %s terminated as request entity was larger than %s")
    IOException requestEntityWasTooLarge(SocketAddress address, long size);

    @Message(id = 20, value = "Connection terminated as request was larger than %s")
    IOException requestEntityWasTooLarge(long size);

    @Message(id = 21, value = "Session already invalidated")
    IllegalStateException sessionAlreadyInvalidated();

    @Message(id = 22, value = "The specified hash algorithm '%s' can not be found.")
    IllegalArgumentException hashAlgorithmNotFound(String algorithmName);

    @Message(id = 23, value = "An invalid Base64 token has been recieved.")
    IllegalArgumentException invalidBase64Token(@Cause final IOException cause);

    @Message(id = 24, value = "An invalidly formatted nonce has been received.")
    IllegalArgumentException invalidNonceReceived();

    @Message(id = 25, value = "Unexpected token '%s' within header.")
    IllegalArgumentException unexpectedTokenInHeader(final String name);

    @Message(id = 26, value = "Invalid header received.")
    IllegalArgumentException invalidHeader();

    @Message(id = 27, value = "Could not find session cookie config in the request")
    IllegalStateException couldNotFindSessionCookieConfig();

    @Message(id = 28, value = "Session %s already exists")
    IllegalStateException sessionAlreadyExists(final String id);

    @Message(id = 29, value = "Channel was closed mid chunk, if you have attempted to write chunked data you cannot shutdown the channel until after it has all been written.")
    IOException chunkedChannelClosedMidChunk();

    @Message(id = 30, value = "User %s successfuly authenticated.")
    String userAuthenticated(final String userName);

    @Message(id = 31, value = "User %s has logged out.")
    String userLoggedOut(final String userName);

    @Message(id = 33, value = "Authentication type %s cannot be combined with %s")
    IllegalStateException authTypeCannotBeCombined(String type, String existing);

    @Message(id = 34, value = "Stream is closed")
    IOException streamIsClosed();

    @Message(id = 35, value = "Cannot get stream as startBlocking has not been invoked")
    IllegalStateException startBlockingHasNotBeenCalled();

    @Message(id = 36, value = "Connection terminated parsing multipart data")
    IOException connectionTerminatedReadingMultiPartData();

    @Message(id = 37, value = "Failed to parse path in HTTP request")
    RuntimeException failedToParsePath();

    @Message(id = 38, value = "Authentication failed, requested user name '%s'")
    String authenticationFailed(final String userName);

    @Message(id = 39, value = "To many query parameters, cannot have more than %s query parameters")
    RuntimeException tooManyQueryParameters(int noParams);

    @Message(id = 40, value = "To many headers, cannot have more than %s header")
    RuntimeException tooManyHeaders(int noParams);

    @Message(id = 41, value = "Channel is closed")
    ClosedChannelException channelIsClosed();

    @Message(id = 42, value = "Could not decode trailers in HTTP request")
    IOException couldNotDecodeTrailers();

    @Message(id = 43, value = "Data is already being sent. You must wait for the completion callback to be be invoked before calling send() again")
    IllegalStateException dataAlreadyQueued();

    @Message(id = 44, value = "More than one predicate with name %s. Builder class %s and %s")
    IllegalStateException moreThanOnePredicateWithName(String name, Class<? extends PredicateBuilder> aClass,
            Class<? extends PredicateBuilder> existing);

    @Message(id = 45, value = "Error parsing predicate string %s:%n%s")
    IllegalArgumentException errorParsingPredicateString(String reason, String s);

    @Message(id = 46, value = "The number of cookies sent exceeded the maximum of %s")
    IllegalStateException tooManyCookies(int maxCookies);

    @Message(id = 47, value = "The number of parameters exceeded the maximum of %s")
    IllegalStateException tooManyParameters(int maxValues);

    @Message(id = 48, value = "No request is currently active")
    IllegalStateException noRequestActive();

    @Message(id = 50, value = "AuthenticationMechanism Outcome is null")
    IllegalStateException authMechanismOutcomeNull();
}
