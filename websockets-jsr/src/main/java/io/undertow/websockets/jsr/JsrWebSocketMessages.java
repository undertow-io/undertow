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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import javax.websocket.Decoder;
import javax.websocket.DeploymentException;

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;


/**
 * start at 3000
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@MessageBundle(projectCode = "UT")
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

    @Message(id = 3005, value = "MessageHandler for type %s already registered")
    IllegalStateException handlerAlreadyRegistered(AbstractFrameHandler.FrameType frameType);

    @Message(id = 3006, value = "Unable to detect FrameType for clazz %s")
    IllegalStateException unsupportedFrameType(Class<?> clazz);

    @Message(id = 3007, value = "Unable to detect MessageHandler type for %s")
    IllegalStateException unknownHandlerType(Class<?> clazz);

    @Message(id = 3008, value = "Unable to detect Encoder type for %s")
    IllegalStateException unknownEncoderType(Class<?> clazz);

    @Message(id = 3009, value = "More than one %s parameter for %s")
    IllegalArgumentException moreThanOneParameterOfType(Class<?> type, Method method);

    @Message(id = 3010, value = "No parameter of type %s found in method %s")
    IllegalArgumentException parameterNotFound(Class<?> type, Method method);

    @Message(id = 3011, value = "More than one method is annotated with %s")
    DeploymentException moreThanOneAnnotation(Class<?> clazz);

    @Message(id = 3012, value = "Method %s has invalid parameters %s")
    DeploymentException invalidParamers(Method method, Set<Integer> allParams);

    @Message(id = 3014, value = "Could not determine decoder type for %s")
    IllegalArgumentException couldNotDetermineDecoderTypeFor(Class<?> decoderClass);

    @Message(id = 3015, value = "No decoder accepted message %s")
    String noDecoderAcceptedMessage(List<? extends Decoder> decoders);

    @Message(id = 3016, value = "Cannot send in middle of fragmeneted message")
    IllegalStateException cannotSendInMiddleOfFragmentedMessage();
}
