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

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;

import io.undertow.server.HttpServerConnection;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Cause;
import org.jboss.logging.LogMessage;
import org.jboss.logging.Logger;
import org.jboss.logging.Message;
import org.jboss.logging.MessageLogger;
import org.xnio.IoFuture;

/**
 * log messages start at 5000
 *
 * @author Stuart Douglas
 */
@MessageLogger(projectCode = "UNDERTOW")
public interface UndertowLogger extends BasicLogger {

    UndertowLogger ROOT_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName());

    UndertowLogger REQUEST_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".request");

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5001, value = "An exception occurred processing the request")
    void exceptionProcessingRequest(@Cause Throwable cause);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5002, value = "An exception occurred getting the session")
    void getSessionFailed(@Cause IOException exception);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5003, value = "Unexpected state in session callback %s")
    void unexpectedStatusGettingSession(IoFuture.Status status);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5004, value = "Could not send session cookie as response has already started")
    void couldNotSendSessionCookieAsResponseAlreadyStarted();

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5005, value = "Could not invalidate session cookie as response has already started")
    void couldNotInvalidateSessionCookieAsResponseAlreadyStarted();

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5006, value = "Could not find session cookie config in the request, session will not be persistent across requests")
    void couldNotFindSessionCookieConfig();

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5007, value = "Configured error page %s was not found")
    void errorPageDoesNotExist(File file);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5008, value = "Exception reading error page %s")
    void errorLoadingErrorPage(@Cause final IOException e, final File file);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 5009, value = "Exception reading file %s: %s")
    void exceptionReadingFile(final File file, final IOException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5010, value = "IOException writing to channel")
    void ioExceptionWritingToChannel(@Cause IOException e);


    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5011, value = "IOException writing to channel")
    void ioExceptionClosingChannel(@Cause IOException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5012, value = "File was requested outside the handlers base directory. Installing a canonical path " +
            "handler in front of the file handler will prevent this")
    void fileHandlerWithoutCanonicalPathHandler();

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5013, value = "IOException reading from channel")
    void ioExceptionReadingFromChannel(@Cause IOException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5014, value = "Connection terminated parsing multipart data")
    void connectionTerminatedReadingMultiPartData();

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5015, value = "Cannot remove uploaded file %s")
    void cannotRemoveUploadedFile(File file);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 5016, value = "Connection from %s terminated as request header was larger than %s")
    void requestHeaderWasTooLarge(SocketAddress address, int size);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 5017, value = "Request was not fully consumed")
    void requestWasNotFullyConsumed();
}
