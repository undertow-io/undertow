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

package io.undertow.servlet;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import static org.jboss.logging.Logger.Level.ERROR;

/**
 * log messages start at 15000
 *
 * @author Stuart Douglas
 */
@MessageLogger(projectCode = "UT")
public interface UndertowServletLogger extends BasicLogger {

    UndertowServletLogger ROOT_LOGGER = Logger.getMessageLogger(UndertowServletLogger.class, UndertowServletLogger.class.getPackage().getName());

    UndertowServletLogger REQUEST_LOGGER = Logger.getMessageLogger(UndertowServletLogger.class, UndertowServletLogger.class.getPackage().getName() + ".request");

    @LogMessage(level = ERROR)
    @Message(id = 15000, value = "IOException handling request")
    void ioExceptionHandingRequest(@Cause IOException e);

    @LogMessage(level = ERROR)
    @Message(id = 15001, value = "ServletException handling request")
    void servletExceptionHandlingRequest(@Cause ServletException e);

    @LogMessage(level = ERROR)
    @Message(id = 15002, value = "Stopping servlet %s due to permanent unavailability")
    void stoppingServletDueToPermanentUnavailability(final String servlet, @Cause UnavailableException e);

    @LogMessage(level = ERROR)
    @Message(id = 15003, value = "Stopping servlet %s till %s due to temporary unavailability")
    void stoppingServletUntilDueToTemporaryUnavailability(String name, Date till, @Cause UnavailableException e);

    @LogMessage(level = ERROR)
    @Message(id = 15004, value = "Malformed URL exception reading resource %s")
    void malformedUrlException(String relativePath, @Cause MalformedURLException e);

    @LogMessage(level = ERROR)
    @Message(id = 15005, value = "Error invoking method %s on listener %s")
    void errorInvokingListener(final String method, Class<?> listenerClass, @Cause Exception e);

    @LogMessage(level = ERROR)
    @Message(id = 15006, value = "IOException dispatching async event")
    void ioExceptionDispatchingAsyncEvent(@Cause IOException e);


    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 15007, value = "Stack trace on error enabled for deployment %s, please do not enable for production use")
    void servletStackTracesAll(String deploymentName);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 15008, value = "Failed to load development mode persistent sessions")
    void failedtoLoadPersistentSessions(@Cause Exception e);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 15009, value = "Failed to persist session attribute %s with value %s for session %s")
    void failedToPersistSessionAttribute(String attributeName, Object value, String sessionID, @Cause Exception e);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 15010, value = "Failed to persist sessions")
    void failedToPersistSessions(@Cause Exception e);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 15011, value = "Non standard filter mapping '*' for filter %s. Portable application should use '/*' instead.")
    void nonStandardFilterMapping(String filterName);

    @LogMessage(level = ERROR)
    @Message(id = 15012, value = "Failed to generate error page %s for original exception: %s. Generating error page resulted in a %s.")
    void errorGeneratingErrorPage(String originalErrorPage, Object originalException, int code,  @Cause Throwable cause);

    @Message(id = 15013, value = "Error opening rewrite configuration")
    String errorOpeningRewriteConfiguration();

    @Message(id = 15014, value = "Error reading rewrite configuration")
    @LogMessage(level = ERROR)
    void errorReadingRewriteConfiguration(@Cause IOException e);

    @Message(id = 15015, value = "Error reading rewrite configuration: %s")
    IllegalArgumentException invalidRewriteConfiguration(String line);

    @Message(id = 15016, value = "Invalid rewrite map class: %s")
    IllegalArgumentException invalidRewriteMap(String className);

    @Message(id = 15017, value = "Error reading rewrite flags in line %s as %s")
    IllegalArgumentException invalidRewriteFlags(String line, String flags);

    @Message(id = 15018, value = "Error reading rewrite flags in line %s")
    IllegalArgumentException invalidRewriteFlags(String line);

    @LogMessage(level = ERROR)
    @Message(id = 15019, value = "Failed to destroy %s")
    void failedToDestroy(Object object, @Cause Exception e);
}
