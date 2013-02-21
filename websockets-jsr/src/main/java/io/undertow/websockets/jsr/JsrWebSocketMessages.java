/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

import javax.websocket.DeploymentException;
import java.io.IOException;


/**
 * start at 3000
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@MessageBundle(projectCode = "UNDERTOW")
public interface JsrWebSocketMessages {

    JsrWebSocketMessages MESSAGES = Messages.getBundle(JsrWebSocketMessages.class);

    @Message(id = 3001, value = "PongMessage not supported with MessageHandler.Async")
    IllegalStateException pongMessageNotSupported();

    @Message(id = 3002, value = "SendStream is closed")
    IOException sendStreamClosed();

    @Message(id = 3003, value = "SendWriter is closed")
    IOException sendWriterClosed();

    @Message(id = 3004, value = "Client not supported")
    DeploymentException clientNotSupported();

    @Message(id = 3005, value="MessageHandler for type %s already registered")
    IllegalStateException handlerAlreadyRegistered(AbstractFrameHandler.FrameType frameType);

    @Message(id = 3006, value="Unable to detect FrameType for clazz %s")
    IllegalStateException unsupportedFrameType(Class<?> clazz);

    @Message(id = 3007, value="Unable to instance Endpoint for %s")
    IllegalStateException unableToInstanceEndpoint(Class<?> clazz);

    @Message(id = 3008, value="Unable to detect MessageHandler type for %s")
    IllegalStateException unkownHandlerType(Class<?> clazz);

    @Message(id = 3009, value="Unable to detect Encoder type for %s")
    IllegalStateException unkownEncoderType(Class<?> clazz);
}
