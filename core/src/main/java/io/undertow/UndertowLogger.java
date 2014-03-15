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

import io.undertow.client.ClientConnection;
import io.undertow.server.ServerConnection;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.sql.SQLException;

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
    UndertowLogger REQUEST_DUMPER_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".request.dump");
    /**
     * Logger used for IO exceptions. Generally these should be suppressed, because they are of little interest, and it is easy for an
     * attacker to fill up the logs by intentionally causing IO exceptions.
     */
    UndertowLogger REQUEST_IO_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".request.io");

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5001, value = "An exception occurred processing the request")
    void exceptionProcessingRequest(@Cause Throwable cause);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 5002, value = "Exception reading file %s: %s")
    void exceptionReadingFile(final File file, final IOException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5003, value = "IOException reading from channel")
    void ioExceptionReadingFromChannel(@Cause IOException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5005, value = "Cannot remove uploaded file %s")
    void cannotRemoveUploadedFile(File file);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5006, value = "Connection from %s terminated as request header was larger than %s")
    void requestHeaderWasTooLarge(SocketAddress address, int size);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 5007, value = "Request was not fully consumed")
    void requestWasNotFullyConsumed();

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 5008, value = "An invalid token '%s' with value '%s' has been received.")
    void invalidTokenReceived(final String tokenName, final String tokenValue);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 5009, value = "A mandatory token %s is missing from the request.")
    void missingAuthorizationToken(final String tokenName);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 5010, value = "Verification of authentication tokens for user '%s' has failed using mechanism '%s'.")
    void authenticationFailed(final String userName, final String mechanism);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5011, value = "Ignoring AJP request with prefix %s")
    void ignoringAjpRequestWithPrefixCode(byte prefix);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 5013, value = "An IOException occurred")
    void ioException(@Cause IOException e);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 5014, value = "Failed to parse HTTP request")
    void failedToParseRequest(@Cause Exception e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5015, value = "Error rotating access log")
    void errorRotatingAccessLog(@Cause IOException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5016, value = "Error writing access log")
    void errorWritingAccessLog(@Cause IOException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5017, value = "Unknown variable %s")
    void unknownVariable(String token);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5018, value = "Exception invoking close listener %s")
    void exceptionInvokingCloseListener(ServerConnection.CloseListener l, @Cause Throwable e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5019, value = "Cannot upgrade connection")
    void cannotUpgradeConnection(@Cause Exception e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5020, value = "Error writing JDBC log")
    void errorWritingJDBCLog(@Cause SQLException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5021, value = "Proxy request to %s timed out")
    void proxyRequestTimedOut(String requestURI);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5022, value = "Exception generating error page %s")
    void exceptionGeneratingErrorPage(@Cause Exception e, String location);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5023, value = "Exception handling request to %s")
    void exceptionHandlingRequest(@Cause Throwable t, String requestURI);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5024, value = "Could not register resource change listener for caching resource manager, automatic invalidation of cached resource will not work")
    void couldNotRegisterChangeListener(@Cause Exception e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5025, value = "Could not initiate SPDY connection and no HTTP fallback defined")
    void couldNotInitiateSpdyConnection();
}
