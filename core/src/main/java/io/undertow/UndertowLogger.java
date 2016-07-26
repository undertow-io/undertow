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

import io.undertow.client.ClientConnection;
import io.undertow.protocols.ssl.SslConduit;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.xnio.ssl.SslConnection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

/**
 * log messages start at 5000
 *
 * @author Stuart Douglas
 */
@MessageLogger(projectCode = "UT")
public interface UndertowLogger extends BasicLogger {

    UndertowLogger ROOT_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName());
    UndertowLogger CLIENT_LOGGER = Logger.getMessageLogger(UndertowLogger.class, ClientConnection.class.getPackage().getName());

    UndertowLogger REQUEST_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".request");
    UndertowLogger SESSION_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".session");
    UndertowLogger SECURITY_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".request.security");
    UndertowLogger PROXY_REQUEST_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".proxy");
    UndertowLogger REQUEST_DUMPER_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".request.dump");
    /**
     * Logger used for IO exceptions. Generally these should be suppressed, because they are of little interest, and it is easy for an
     * attacker to fill up the logs by intentionally causing IO exceptions.
     */
    UndertowLogger REQUEST_IO_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".request.io");
    UndertowLogger ERROR_RESPONSE = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".request.error-response");

    @LogMessage(level = ERROR)
    @Message(id = 5001, value = "An exception occurred processing the request")
    void exceptionProcessingRequest(@Cause Throwable cause);

//    @LogMessage(level = INFO)
//    @Message(id = 5002, value = "Exception reading file %s: %s")
//    void exceptionReadingFile(final Path file, final IOException e);

    @LogMessage(level = ERROR)
    @Message(id = 5003, value = "IOException reading from channel")
    void ioExceptionReadingFromChannel(@Cause IOException e);

    @LogMessage(level = ERROR)
    @Message(id = 5005, value = "Cannot remove uploaded file %s")
    void cannotRemoveUploadedFile(Path file);

    @LogMessage(level = ERROR)
    @Message(id = 5006, value = "Connection from %s terminated as request header was larger than %s")
    void requestHeaderWasTooLarge(SocketAddress address, int size);

    @LogMessage(level = DEBUG)
    @Message(id = 5007, value = "Request was not fully consumed")
    void requestWasNotFullyConsumed();

    @LogMessage(level = DEBUG)
    @Message(id = 5008, value = "An invalid token '%s' with value '%s' has been received.")
    void invalidTokenReceived(final String tokenName, final String tokenValue);

    @LogMessage(level = DEBUG)
    @Message(id = 5009, value = "A mandatory token %s is missing from the request.")
    void missingAuthorizationToken(final String tokenName);

    @LogMessage(level = DEBUG)
    @Message(id = 5010, value = "Verification of authentication tokens for user '%s' has failed using mechanism '%s'.")
    void authenticationFailed(final String userName, final String mechanism);

    @LogMessage(level = ERROR)
    @Message(id = 5011, value = "Ignoring AJP request with prefix %s")
    void ignoringAjpRequestWithPrefixCode(byte prefix);

    @LogMessage(level = DEBUG)
    @Message(id = 5013, value = "An IOException occurred")
    void ioException(@Cause IOException e);

    @LogMessage(level = DEBUG)
    @Message(id = 5014, value = "Failed to parse HTTP request")
    void failedToParseRequest(@Cause Exception e);

    @LogMessage(level = ERROR)
    @Message(id = 5015, value = "Error rotating access log")
    void errorRotatingAccessLog(@Cause IOException e);

    @LogMessage(level = ERROR)
    @Message(id = 5016, value = "Error writing access log")
    void errorWritingAccessLog(@Cause IOException e);

    @LogMessage(level = ERROR)
    @Message(id = 5017, value = "Unknown variable %s. For the literal percent character use two percent characters: '%%'")
    void unknownVariable(String token);

    @LogMessage(level = ERROR)
    @Message(id = 5018, value = "Exception invoking close listener %s")
    void exceptionInvokingCloseListener(ServerConnection.CloseListener l, @Cause Throwable e);

//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 5019, value = "Cannot upgrade connection")
//    void cannotUpgradeConnection(@Cause Exception e);

    @LogMessage(level = ERROR)
    @Message(id = 5020, value = "Error writing JDBC log")
    void errorWritingJDBCLog(@Cause SQLException e);

//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 5021, value = "Proxy request to %s timed out")
//    void proxyRequestTimedOut(String requestURI);

    @LogMessage(level = ERROR)
    @Message(id = 5022, value = "Exception generating error page %s")
    void exceptionGeneratingErrorPage(@Cause Exception e, String location);

    @LogMessage(level = ERROR)
    @Message(id = 5023, value = "Exception handling request to %s")
    void exceptionHandlingRequest(@Cause Throwable t, String requestURI);

    @LogMessage(level = ERROR)
    @Message(id = 5024, value = "Could not register resource change listener for caching resource manager, automatic invalidation of cached resource will not work")
    void couldNotRegisterChangeListener(@Cause Exception e);

//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 5025, value = "Could not initiate SPDY connection and no HTTP fallback defined")
//    void couldNotInitiateSpdyConnection();

    @LogMessage(level = INFO)
    @Message(id = 5026, value = "Jetty ALPN support not found on boot class path, %s client will not be available.")
    void jettyALPNNotFound(String protocol);

    @LogMessage(level = ERROR)
    @Message(id = 5027, value = "Timing out request to %s")
    void timingOutRequest(String requestURI);

    @LogMessage(level = ERROR)
    @Message(id = 5028, value = "Proxy request to %s failed")
    void proxyRequestFailed(String requestURI, @Cause Exception e);

//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 5030, value = "Proxy request to %s could not resolve a backend server")
//    void proxyRequestFailedToResolveBackend(String requestURI);

    @LogMessage(level = ERROR)
    @Message(id = 5031, value = "Proxy request to %s could not connect to backend server %s")
    void proxyFailedToConnectToBackend(String requestURI, URI uri);

    @LogMessage(level = ERROR)
    @Message(id = 5032, value = "Listener not making progress on framed channel, closing channel to prevent infinite loop")
    void listenerNotProgressing();

//    @LogMessage(level = Logger.Level.ERROR)
//    @Message(id = 5033, value = "Failed to initiate HTTP2 connection")
//    void couldNotInitiateHttp2Connection();

    @LogMessage(level = ERROR)
    @Message(id = 5034, value = "Remote endpoint failed to send initial settings frame in HTTP2 connection, frame type %s")
    void remoteEndpointFailedToSendInitialSettings(int type);

    @LogMessage(level = DEBUG)
    @Message(id = 5035, value = "Closing channel because of parse timeout for remote address %s")
    void parseRequestTimedOut(java.net.SocketAddress remoteAddress);

    @LogMessage(level = ERROR)
    @Message(id = 5036, value = "ALPN negotiation failed for %s and no fallback defined, closing connection")
    void noALPNFallback(SocketAddress address);

    /**
     * Undertow mod_cluster proxy messages
     */
    @LogMessage(level = WARN)
    @Message(id = 5037, value = "Name of the cookie containing the session id, %s, had been too long and was truncated to: %s")
    void stickySessionCookieLengthTruncated(String original, String current);

    @LogMessage(level = DEBUG)
    @Message(id = 5038, value = "Balancer created: id: %s, name: %s, stickySession: %s, stickySessionCookie: %s, stickySessionPath: %s, stickySessionRemove: %s, stickySessionForce: %s, waitWorker: %s, maxattempts: %s")
    void balancerCreated(int id, String name, boolean stickySession, String stickySessionCookie, String stickySessionPath, boolean stickySessionRemove,
                                            boolean stickySessionForce, int waitWorker, int maxattempts);

    @LogMessage(level = INFO)
    @Message(id = 5039, value = "Undertow starts mod_cluster proxy advertisements on %s with frequency %s ms")
    void proxyAdvertisementsStarted(String address, int frequency);

    @LogMessage(level = DEBUG)
    @Message(id = 5040, value = "Gonna send payload:\n%s")
    void proxyAdvertiseMessagePayload(String payload);

    @LogMessage(level = ERROR)
    @Message(id = 5041, value = "Cannot send advertise message. Address: %s")
    void proxyAdvertiseCannotSendMessage(@Cause Exception e, InetSocketAddress address);

    @LogMessage(level = DEBUG)
    @Message(id = 5042, value = "Undertow mod_cluster proxy MCMPHandler created")
    void mcmpHandlerCreated();

    @LogMessage(level = ERROR)
    @Message(id = 5043, value = "Error in processing MCMP commands: Type:%s, Mess: %s")
    void mcmpProcessingError(String type, String errString);

    @LogMessage(level = INFO)
    @Message(id = 5044, value = "Removing node %s")
    void removingNode(String jvmRoute);

    // Aliases intentionally omitted from INFO level.
    @LogMessage(level = INFO)
    @Message(id = 5045, value = "Registering context %s, for node %s")
    void registeringContext(String contextPath, String jvmRoute);

    // Context path and JVMRoute redundantly logged with DEBUG soa s to provide meaning for aliases.
    @LogMessage(level = DEBUG)
    @Message(id = 5046, value = "Registering context %s, for node %s, with aliases %s")
    void registeringContext(String contextPath, String jvmRoute, List<String> aliases);

    @LogMessage(level = INFO)
    @Message(id = 5047, value = "Unregistering context %s, from node %s")
    void unregisteringContext(String contextPath, String jvmRoute);

    @LogMessage(level = DEBUG)
    @Message(id = 5048, value = "Node %s in error")
    void nodeIsInError(String jvmRoute);

    @LogMessage(level = DEBUG)
    @Message(id = 5049, value = "NodeConfig created: connectionURI: %s, balancer: %s, load balancing group: %s, jvmRoute: %s, flushPackets: %s, flushwait: %s, ping: %s," +
            "ttl: %s, timeout: %s, maxConnections: %s, cacheConnections: %s, requestQueueSize: %s, queueNewRequests: %s")
    void nodeConfigCreated(URI connectionURI, String balancer, String domain, String jvmRoute, boolean flushPackets, int flushwait, int ping, long ttl,
                           int timeout, int maxConnections, int cacheConnections, int requestQueueSize, boolean queueNewRequests);

    @LogMessage(level = ERROR)
    @Message(id = 5050, value = "Failed to process management request")
    void failedToProcessManagementReq(@Cause Exception e);

    @LogMessage(level = ERROR)
    @Message(id = 5051, value = "Failed to send ping response")
    void failedToSendPingResponse(@Cause Exception e);

    @LogMessage(level = DEBUG)
    @Message(id = 5052, value = "Failed to send ping response, node.getJvmRoute(): %s, jvmRoute: %s")
    void failedToSendPingResponseDBG(@Cause Exception e, String node, String jvmRoute);

    @LogMessage(level = INFO)
    @Message(id = 5053, value = "Registering node %s, connection: %s")
    void registeringNode(String jvmRoute, URI connectionURI);

    @LogMessage(level = DEBUG)
    @Message(id = 5054, value = "MCMP processing, key: %s, value: %s")
    void mcmpKeyValue(HttpString name, String value);

    @LogMessage(level = DEBUG)
    @Message(id = 5055, value = "HttpClientPingTask run for connection: %s")
    void httpClientPingTask(URI connection);

    @LogMessage(level = DEBUG)
    @Message(id = 5056, value = "Received node load in STATUS message, node jvmRoute: %s, load: %s")
    void receivedNodeLoad(String jvmRoute, String loadValue);

    @LogMessage(level = DEBUG)
    @Message(id = 5057, value = "Sending MCMP response to destination: %s, HTTP status: %s, Headers: %s, response: %s")
    void mcmpSendingResponse(InetSocketAddress destination, int status, HeaderMap headers, String response);

    @LogMessage(level = WARN)
    @Message(id = 5058, value = "Could not bind multicast socket to %s (%s address): %s; make sure your multicast address is of the same type as the IP stack (IPv4 or IPv6). Multicast socket will not be bound to an address, but this may lead to cross talking (see http://www.jboss.org/community/docs/DOC-9469 for details).")
    void potentialCrossTalking(InetAddress group, String s, String localizedMessage);

    @LogMessage(level = WARN)
    @Message(id = 5060, value = "Predicate %s uses old style square braces to define predicates, which will be removed in a future release. predicate[value] should be changed to predicate(value)")
    void oldStylePredicateSyntax(String string);

    @Message(id=5061, value = "More than %s restarts detected, breaking assumed infinite loop")
    IllegalStateException maxRestartsExceeded(int maxRestarts);

    @LogMessage(level = ERROR)
    @Message(id = 5062, value = "Pattern parse error")
    void extendedAccessLogPatternParseError(@Cause Throwable t);

    @LogMessage(level = ERROR)
    @Message(id = 5063, value = "Unable to decode with rest of chars starting: %s")
    void extendedAccessLogUnknownToken(String token);

    @LogMessage(level = ERROR)
    @Message(id = 5064, value = "No closing ) found for in decode")
    void extendedAccessLogMissingClosing();

    @LogMessage(level = ERROR)
    @Message(id = 5065, value = "The next characters couldn't be decoded: %s")
    void extendedAccessLogCannotDecode(String chars);

    @LogMessage(level = ERROR)
    @Message(id = 5066, value = "X param for servlet request, couldn't decode value: %s")
    void extendedAccessLogCannotDecodeXParamValue(String value);

    @LogMessage(level = ERROR)
    @Message(id = 5067, value = "X param in wrong format. Needs to be 'x-#(...)'")
    void extendedAccessLogBadXParam();

    @LogMessage(level = INFO)
    @Message(id = 5068, value = "Pattern was just empty or whitespace")
    void extendedAccessLogEmptyPattern();

    @LogMessage(level = ERROR)
    @Message(id = 5069, value = "Failed to write JDBC access log")
    void failedToWriteJdbcAccessLog(@Cause SQLException e);

    @LogMessage(level = ERROR)
    @Message(id = 5070, value = "Failed to write pre-cached file")
    void failedToWritePreCachedFile();

    @LogMessage(level = ERROR)
    @Message(id = 5071, value = "Undertow request failed %s")
    void undertowRequestFailed(@Cause Throwable t, HttpServerExchange exchange);

    @LogMessage(level = WARN)
    @Message(id = 5072, value = "Thread %s (id=%s) has been active for %s milliseconds (since %s) to serve the same request for %s and may be stuck (configured threshold for this StuckThreadDetectionValve is %s seconds). There is/are %s thread(s) in total that are monitored by this Valve and may be stuck.")
    void stuckThreadDetected(String threadName, long threadId, long active, Date start, String requestUri, int threshold, int stuckCount, @Cause Throwable stackTrace);

    @LogMessage(level = WARN)
    @Message(id = 5073, value = "Thread %s (id=%s) was previously reported to be stuck but has completed. It was active for approximately %s milliseconds. There is/are still %s thread(s) that are monitored by this Valve and may be stuck.")
    void stuckThreadCompleted(String threadName, long threadId, long active, int stuckCount);

    @LogMessage(level = ERROR)
    @Message(id = 5074, value = "Failed to invoke error callback %s for SSE task")
    void failedToInvokeFailedCallback(ServerSentEventConnection.EventCallback callback, @Cause Exception e);

    @Message(id = 5075, value = "Unable to resolve mod_cluster management host's address for '%s'")
    IllegalStateException unableToResolveModClusterManagementHost(String providedHost);

    @LogMessage(level = ERROR)
    @Message(id = 5076, value = "SSL read loop detected. This should not happen, please report this to the Undertow developers. Current state %s")
    void sslReadLoopDetected(SslConduit sslConduit);

    @LogMessage(level = ERROR)
    @Message(id = 5077, value = "SSL unwrap buffer overflow detected. This should not happen, please report this to the Undertow developers. Current state %s")
    void sslBufferOverflow(SslConduit sslConduit);

    @LogMessage(level = ERROR)
    @Message(id = 5078, value = "ALPN connection failed")
    void alpnConnectionFailed(@Cause Exception e);

    @LogMessage(level = ERROR)
    @Message(id = 5079, value = "ALPN negotiation on %s failed")
    void alpnConnectionFailed(SslConnection connection);

    @LogMessage(level = ERROR)
    @Message(id = 5080, value = "HttpServerExchange cannot have both async IO resumed and dispatch() called in the same cycle")
    void resumedAndDispatched();
}
