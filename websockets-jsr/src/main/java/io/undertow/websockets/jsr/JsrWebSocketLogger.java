/*
 * JBoss, Home of Professional Open Source.
 * Copyright 203 Red Hat, Inc., and individual contributors
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

package io.undertow.websockets.jsr;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

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
    @Message(id = 26001, value = "Unable to instance endpoint")
    void endpointCreationFailed(@Cause InstantiationException cause);
}
