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
import io.undertow.server.handlers.builder.HandlerBuilder;
import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

import javax.net.ssl.SSLPeerUnverifiedException;

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

    @Message(id = 12, value = "Session manager was not attached to the request. Make sure that the SessionAttachmentHander is installed in the handler chain")
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

    @Message(id = 23, value = "An invalid Base64 token has been received.")
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

    @Message(id = 30, value = "User %s successfully authenticated.")
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
    IllegalStateException moreThanOnePredicateWithName(String name, Class<? extends PredicateBuilder> aClass, Class<? extends PredicateBuilder> existing);

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

    @Message(id = 51, value = "Not a valid IP pattern %s")
    IllegalArgumentException notAValidIpPattern(String peer);

    @Message(id = 52, value = "Session data requested when non session based authentication in use")
    IllegalStateException noSessionData();

    @Message(id = 53, value = "Listener %s already registered")
    IllegalArgumentException listenerAlreadyRegistered(String name);

    @Message(id = 54, value = "The maximum size %s for an individual file in a multipart request was exceeded")
    IOException maxFileSizeExceeded(long maxIndividualFileSize);

    @Message(id = 55, value = "Could not set attribute %s to %s as it is read only")
    String couldNotSetAttribute(String attributeName, String newValue);

    @Message(id = 56, value = "Could not parse URI template %s, exception at char %s")
    RuntimeException couldNotParseUriTemplate(String path, int i);

    @Message(id = 57, value = "Mismatched braces in attribute string %s")
    RuntimeException mismatchedBraces(String valueString);

    @Message(id = 58, value = "More than one handler with name %s. Builder class %s and %s")
    IllegalStateException moreThanOneHandlerWithName(String name, Class<? extends HandlerBuilder> aClass, Class<? extends HandlerBuilder> existing);

    @Message(id = 59, value = "Invalid syntax %s")
    IllegalArgumentException invalidSyntax(String line);

    @Message(id = 60, value = "Error parsing handler string %s:%n%s")
    IllegalArgumentException errorParsingHandlerString(String reason, String s);

    @Message(id = 61, value = "Out of band responses only allowed for 100-continue requests")
    IllegalArgumentException outOfBandResponseOnlyAllowedFor100Continue();

    @Message(id = 62, value = "AJP does not support HTTP upgrade")
    IllegalStateException ajpDoesNotSupportHTTPUpgrade();

    @Message(id = 63, value = "File system watcher already started")
    IllegalStateException fileSystemWatcherAlreadyStarted();

    @Message(id = 64, value = "File system watcher not started")
    IllegalStateException fileSystemWatcherNotStarted();

    @Message(id = 65, value = "SSL must be specified to connect to a https URL")
    IllegalArgumentException sslWasNull();

    @Message(id = 66, value = "Incorrect magic number for AJP packet header")
    IOException wrongMagicNumber();

    @Message(id = 67, value = "No client cert was provided")
    SSLPeerUnverifiedException peerUnverified();

    @Message(id = 68, value = "Servlet path match failed")
    IllegalArgumentException servletPathMatchFailed();

    @Message(id = 69, value = "Could not parse set cookie header %s")
    IllegalArgumentException couldNotParseCookie(String headerValue);

    @Message(id = 70, value = "method can only be called by IO thread")
    IllegalStateException canOnlyBeCalledByIoThread();

    @Message(id = 71, value = "Cannot add path template %s, matcher already contains an equivalent pattern %s")
    IllegalStateException matcherAlreadyContainsTemplate(String templateString, String templateString1);

    @Message(id = 72, value = "Failed to decode url %s to charset %s")
    IllegalArgumentException failedToDecodeURL(String s, String enc);

    @Message(id = 73, value = "Resource change listeners are not supported")
    IllegalArgumentException resourceChangeListenerNotSupported();

    @Message(id = 74, value = "Could not renegotiate SSL connection to require client certificate, as client had sent more data")
    IllegalStateException couldNotRenegotiate();

    @Message(id = 75, value = "Object was freed")
    IllegalStateException objectWasFreed();

    @Message(id = 76, value = "Handler not shutdown")
    IllegalStateException handlerNotShutdown();

    @Message(id = 77, value = "The underlying transport does not support HTTP upgrade")
    IllegalStateException upgradeNotSupported();

    @Message(id = 78, value = "Renegotiation not supported")
    IOException renegotiationNotSupported();

    @Message(id = 79, value = "Not a valid user agent pattern %s")
    IllegalArgumentException notAValidUserAgentPattern(String userAgent);

    @Message(id = 80, value = "Not a valid regular expression pattern %s")
    IllegalArgumentException notAValidRegularExpressionPattern(String pattern);

    @Message(id = 81, value = "Bad request")
    RuntimeException badRequest();

    @Message(id = 82, value = "Host %s already registered")
    RuntimeException hostAlreadyRegistered(Object host);

    @Message(id = 83, value = "Host %s has not been registered")
    RuntimeException hostHasNotBeenRegistered(Object host);

    @Message(id = 84, value = "Attempted to write additional data after the last chunk")
    IOException extraDataWrittenAfterChunkEnd();

    @Message(id = 85, value = "Could not generate unique session id")
    RuntimeException couldNotGenerateUniqueSessionId();

    @Message(id = 86, value = "SPDY needs to be provided with a heap buffer pool, for use in compressing and decompressing headers.")
    IllegalArgumentException mustProvideHeapBuffer();

    @Message(id = 87, value = "Unexpected SPDY frame type %s")
    IOException unexpectedFrameType(int type);

    @Message(id = 88, value = "SPDY control frames cannot have body content")
    IOException controlFrameCannotHaveBodyContent();

    @Message(id = 89, value = "SPDY not supported")
    IOException spdyNotSupported();
}
