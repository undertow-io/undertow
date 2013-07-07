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

/**
 * log messages start at 15000
 *
 * @author Stuart Douglas
 */
@MessageLogger(projectCode = "UT")
public interface UndertowServletLogger extends BasicLogger {

    UndertowServletLogger ROOT_LOGGER = Logger.getMessageLogger(UndertowServletLogger.class, UndertowServletLogger.class.getPackage().getName());

    UndertowServletLogger REQUEST_LOGGER = Logger.getMessageLogger(UndertowServletLogger.class, UndertowServletLogger.class.getPackage().getName() + ".request");

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 15000, value = "IOException handling request")
    void ioExceptionHandingRequest(@Cause IOException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 15001, value = "ServletException handling request")
    void servletExceptionHandlingRequest(@Cause ServletException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 15002, value = "Stopping servlet %s due to permanent unavailability")
    void stoppingServletDueToPermanentUnavailability(final String servlet, @Cause UnavailableException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 15003, value = "Stopping servlet %s till %s due to temporary unavailability")
    void stoppingServletUntilDueToTemporaryUnavailability(String name, Date till, @Cause UnavailableException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 15004, value = "Malformed URL exception reading resource %s")
    void malformedUrlException(String relativePath, @Cause MalformedURLException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 15005, value = "Error invoking method %s on listener %s")
    void errorInvokingListener(final String method, Class<?> listenerClass, @Cause Exception e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 15006, value = "IOException dispatching async event")
    void ioExceptionDispatchingAsyncEvent(@Cause IOException e);


    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 15007, value = "Development mode enabled for deployment %s, please do not enable development mode for production use")
    void developmentModeEnabled(String deploymentName);
}
