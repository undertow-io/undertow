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

package io.undertow;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import io.undertow.predicate.PredicateBuilder;
import io.undertow.protocols.http2.HpackException;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.HttpString;
import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

import javax.net.ssl.SSLHandshakeException;
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

    @Message(id = 10, value = "Session is invalid %s")
    IllegalStateException sessionIsInvalid(String sessionId);

    @Message(id = 11, value = "Session manager must not be null")
    IllegalStateException sessionManagerMustNotBeNull();

    @Message(id = 12, value = "Session manager was not attached to the request. Make sure that the SessionAttachmentHandler is installed in the handler chain")
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

    @Message(id = 45, value = "Error parsing predicated handler string %s:%n%s")
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
    IOException sslWasNull();

    @Message(id = 66, value = "Incorrect magic number %s for AJP packet header")
    IOException wrongMagicNumber(int number);

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
    IllegalArgumentException failedToDecodeURL(String s, String enc, @Cause Exception e);


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

//    @Message(id = 89, value = "SPDY not supported")
//    IOException spdyNotSupported();

    @Message(id = 90, value = "No ALPN implementation available (tried Jetty ALPN and JDK9)")
    IOException alpnNotAvailable();

    @Message(id = 91, value = "Buffer has already been freed")
    IllegalStateException bufferAlreadyFreed();

    @Message(id = 92, value = "A SPDY header was too large to fit in a response buffer, if you want to support larger headers please increase the buffer size")
    IllegalStateException headersTooLargeToFitInHeapBuffer();

//    @Message(id = 93, value = "A SPDY stream was reset by the remote endpoint")
//    IOException spdyStreamWasReset();

    @Message(id = 94, value = "Blocking await method called from IO thread. Blocking IO must be dispatched to a worker thread or deadlocks will result.")
    IOException awaitCalledFromIoThread();

    @Message(id = 95, value = "Recursive call to flushSenders()")
    RuntimeException recursiveCallToFlushingSenders();

    @Message(id = 96, value = "More data was written to the channel than specified in the content-length")
    IllegalStateException fixedLengthOverflow();

    @Message(id = 97, value = "AJP request already in progress")
    IllegalStateException ajpRequestAlreadyInProgress();

    @Message(id = 98, value = "HTTP ping data must be 8 bytes in length")
    String httpPingDataMustBeLength8();

    @Message(id = 99, value = "Received a ping of size other than 8")
    String invalidPingSize();

    @Message(id = 100, value = "stream id must be zero for frame type %s")
    String streamIdMustBeZeroForFrameType(int frameType);

    @Message(id = 101, value = "stream id must not be zero for frame type %s")
    String streamIdMustNotBeZeroForFrameType(int frameType);

    @Message(id = 102, value = "RST_STREAM received for idle stream")
    String rstStreamReceivedForIdleStream();

    @Message(id = 103, value = "Http2 stream was reset")
    IOException http2StreamWasReset();

    @Message(id = 104, value = "Incorrect HTTP2 preface")
    IOException incorrectHttp2Preface();

    @Message(id = 105, value = "HTTP2 frame to large")
    IOException http2FrameTooLarge();

    @Message(id = 106, value = "HTTP2 continuation frame received without a corresponding headers or push promise frame")
    IOException http2ContinuationFrameNotExpected();

    @Message(id = 107, value = "Huffman encoded value in HPACK headers did not end with EOS padding")
    HpackException huffmanEncodedHpackValueDidNotEndWithEOS();

    @Message(id = 108, value = "HPACK variable length integer encoded over too many octects, max is %s")
    HpackException integerEncodedOverTooManyOctets(int maxIntegerOctets);

    @Message(id = 109, value = "Zero is not a valid header table index")
    HpackException zeroNotValidHeaderTableIndex();


    @Message(id = 110, value = "Cannot send 100-Continue, getResponseChannel() has already been called")
    IOException cannotSendContinueResponse();

    @Message(id = 111, value = "Parser did not make progress")
    IOException parserDidNotMakeProgress();

    @Message(id = 112, value = "Only client side can call createStream, if you wish to send a PUSH_PROMISE frame use createPushPromiseStream instead")
    IOException headersStreamCanOnlyBeCreatedByClient();

    @Message(id = 113, value = "Only the server side can send a push promise stream")
    IOException pushPromiseCanOnlyBeCreatedByServer();

    @Message(id = 114, value = "Invalid IP access control rule %s. Format is: [ip-match] allow|deny")
    IllegalArgumentException invalidAclRule(String rule);

    @Message(id = 115, value = "Server received PUSH_PROMISE frame from client")
    IOException serverReceivedPushPromise();

    @Message(id = 116, value = "CONNECT not supported by this connector")
    IllegalStateException connectNotSupported();

    @Message(id = 117, value = "Request was not a CONNECT request")
    IllegalStateException notAConnectRequest();

    @Message(id = 118, value = "Cannot reset buffer, response has already been commited")
    IllegalStateException cannotResetBuffer();

    @Message(id = 119, value = "HTTP2 via prior knowledge failed")
    IOException http2PriRequestFailed();

    @Message(id = 120, value = "Out of band responses are not allowed for this connector")
    IllegalStateException outOfBandResponseNotSupported();

    @Message(id = 121, value = "Session was rejected as the maximum number of sessions (%s) has been hit")
    IllegalStateException tooManySessions(int maxSessions);

    @Message(id = 122, value = "CONNECT attempt failed as target proxy returned %s")
    IOException proxyConnectionFailed(int responseCode);

    @Message(id = 123, value = "MCMP message %s rejected due to suspicious characters")
    RuntimeException mcmpMessageRejectedDueToSuspiciousCharacters(String data);

    @Message(id = 124, value = "renegotiation timed out")
    IllegalStateException rengotiationTimedOut();

    @Message(id = 125, value = "Request body already read")
    IllegalStateException requestBodyAlreadyRead();

    @Message(id = 126, value = "Attempted to do blocking IO from the IO thread. This is prohibited as it may result in deadlocks")
    IllegalStateException blockingIoFromIOThread();

    @Message(id = 127, value = "Response has already been sent")
    IllegalStateException responseComplete();

    @Message(id = 128, value = "Remote peer closed connection before all data could be read")
    IOException couldNotReadContentLengthData();

    @Message(id = 129, value = "Failed to send after being safe to send")
    IllegalStateException failedToSendAfterBeingSafe();

    @Message(id = 130, value = "HTTP reason phrase was too large for the buffer. Either provide a smaller message or a bigger buffer. Phrase: %s")
    IllegalStateException reasonPhraseToLargeForBuffer(String phrase);

    @Message(id = 131, value = "Buffer pool is closed")
    IllegalStateException poolIsClosed();

    @Message(id = 132, value = "HPACK decode failed")
    HpackException hpackFailed();

    @Message(id = 133, value = "Request did not contain an Upgrade header, upgrade is not permitted")
    IllegalStateException notAnUpgradeRequest();

    @Message(id = 134, value = "Authentication mechanism %s requires property %s to be set")
    IllegalStateException authenticationPropertyNotSet(String name, String header);

    @Message(id = 135, value = "renegotiation failed")
    IllegalStateException rengotiationFailed();

    @Message(id = 136, value = "User agent charset string must have an even number of items, in the form pattern,charset,pattern,charset,... Instead got: %s")
    IllegalArgumentException userAgentCharsetMustHaveEvenNumberOfItems(String supplied);

    @Message(id = 137, value = "Could not find the datasource called %s")
    IllegalArgumentException datasourceNotFound(String ds);

    @Message(id = 138, value = "Server not started")
    IllegalStateException serverNotStarted();

    @Message(id = 139, value = "Exchange already complete")
    IllegalStateException exchangeAlreadyComplete();

    @Message(id = 140, value = "Initial SSL/TLS data is not a handshake record")
    SSLHandshakeException notHandshakeRecord();

    @Message(id = 141, value = "Initial SSL/TLS handshake record is invalid")
    SSLHandshakeException invalidHandshakeRecord();

    @Message(id = 142, value = "Initial SSL/TLS handshake spans multiple records")
    SSLHandshakeException multiRecordSSLHandshake();

    @Message(id = 143, value = "Expected \"client hello\" record")
    SSLHandshakeException expectedClientHello();

    @Message(id = 144, value = "Expected server hello")
    SSLHandshakeException expectedServerHello();

    @Message(id = 145, value = "Too many redirects")
    IOException tooManyRedirects(@Cause IOException exception);

    @Message(id = 146, value = "HttpServerExchange cannot have both async IO resumed and dispatch() called in the same cycle")
    IllegalStateException resumedAndDispatched();

    @Message(id = 147, value = "No host header in a HTTP/1.1 request")
    IOException noHostInHttp11Request();

    @Message(id = 148, value = "Invalid HPack encoding. First byte: %s")
    HpackException invalidHpackEncoding(byte b);

    @Message(id = 149, value = "HttpString is not allowed to contain newlines. value: %s")
    IllegalArgumentException newlineNotSupportedInHttpString(String value);

    @Message(id = 150, value = "Pseudo header %s received after receiving normal headers. Pseudo headers must be the first headers in a HTTP/2 header block.")
    HpackException pseudoHeaderInWrongOrder(HttpString header);

    @Message(id = 151, value = "Expected to receive a continuation frame")
    String expectedContinuationFrame();

    @Message(id = 152, value = "Incorrect frame size")
    String incorrectFrameSize();

    @Message(id = 153, value = "Stream id not registered")
    IllegalStateException streamNotRegistered();
}
