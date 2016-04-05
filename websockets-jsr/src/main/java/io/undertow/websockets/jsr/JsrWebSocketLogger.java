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

package io.undertow.websockets.jsr;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import javax.websocket.server.PathParam;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * log messages start at 26000
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@MessageLogger(projectCode = "UT")
public interface JsrWebSocketLogger extends BasicLogger {

    JsrWebSocketLogger ROOT_LOGGER = Logger.getMessageLogger(JsrWebSocketLogger.class, JsrWebSocketLogger.class.getPackage().getName());

    JsrWebSocketLogger REQUEST_LOGGER = Logger.getMessageLogger(JsrWebSocketLogger.class, JsrWebSocketLogger.class.getPackage().getName() + ".request");

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 26001, value = "Unable to instantiate endpoint")
    void endpointCreationFailed(@Cause Exception cause);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 26002, value = "Unable to instantiate server configuration %s")
    void couldNotInitializeConfiguration(Class<?> clazz, @Cause Throwable t);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 26003, value = "Adding annotated server endpoint %s for path %s")
    void addingAnnotatedServerEndpoint(Class<?> endpoint, String value);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 26004, value = "Adding annotated client endpoint %s")
    void addingAnnotatedClientEndpoint(Class<?> endpoint);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 26005, value = "Adding programmatic server endpoint %s for path %s")
    void addingProgramaticEndpoint(Class<?> endpointClass, String path);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 26006, value = "Exception running web socket method")
    void exceptionInWebSocketMethod(@Cause Throwable e);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 26007, value = "On Endpoint class %s path param %s on method %s does not reference a valid parameter, valid parameters are %s.")
    void pathTemplateNotFound(Class<?> endpointClass, PathParam param, Method method, Set<String> paths);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 26008, value = "Could not close endpoint on undeploy.")
    void couldNotCloseOnUndeploy(@Cause Exception e);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 26009, value = "XNIO worker was not set on WebSocketDeploymentInfo, the default worker will be used")
    void xnioWorkerWasNull();

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 26010, value = "Buffer pool was not set on WebSocketDeploymentInfo, the default pool will be used")
    void bufferPoolWasNull();

    @Message(id = 26011, value = "XNIO worker was not set on WebSocketDeploymentInfo, and there is no default to use")
    IllegalArgumentException xnioWorkerWasNullAndNoDefault();

    @Message(id = 26012, value = "Buffer pool was not set on WebSocketDeploymentInfo, and there is no default to use")
    IllegalArgumentException bufferPoolWasNullAndNoDefault();
}
