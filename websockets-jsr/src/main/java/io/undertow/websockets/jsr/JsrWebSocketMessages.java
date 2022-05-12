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

import io.undertow.util.PathTemplate;
import io.undertow.websockets.WebSocketExtension;
import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

import jakarta.websocket.Decoder;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Encoder;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;


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
    IllegalStateException handlerAlreadyRegistered(FrameHandler.FrameType frameType);

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

    @Message(id = 3012, value = "Method %s has invalid parameters at locations %s")
    DeploymentException invalidParameters(Method method, Set<Integer> allParams);

    @Message(id = 3014, value = "Could not determine decoder type for %s")
    IllegalArgumentException couldNotDetermineDecoderTypeFor(Class<?> decoderClass);

    @Message(id = 3015, value = "No decoder accepted message %s")
    String noDecoderAcceptedMessage(List<? extends Decoder> decoders);

    @Message(id = 3016, value = "Cannot send in middle of fragmeneted message")
    IllegalStateException cannotSendInMiddleOfFragmentedMessage();

    @Message(id = 3017, value = "Cannot add endpoint after deployment")
    IllegalStateException cannotAddEndpointAfterDeployment();

    @Message(id = 3018, value = "Could not determine type of decode method for class %s")
    DeploymentException couldNotDetermineTypeOfDecodeMethodForClass(Class<? extends Decoder> decoder, @Cause Exception e);

    @Message(id = 3019, value = "Could not determine type of encode method for class %s")
    DeploymentException couldNotDetermineTypeOfEncodeMethodForClass(Class<? extends Encoder> encoder);

    @Message(id = 3020, value = "%s did not implement known decoder interface")
    DeploymentException didNotImplementKnownDecoderSubclass(Class<? extends Decoder> decoder);

    @Message(id = 3021, value = "%s does not have default constructor")
    DeploymentException classDoesNotHaveDefaultConstructor(Class<?> c, @Cause NoSuchMethodException e);

    @Message(id = 3023, value = "Multiple endpoints with the same logical mapping %s and %s")
    DeploymentException multipleEndpointsWithOverlappingPaths(PathTemplate template, PathTemplate existing);

    @Message(id = 3024, value = "Web socket deployment failed")
    DeploymentException couldNotDeploy(@Cause Exception e);

    @Message(id = 3025, value = "Cannot connect until deployment is complete")
    IllegalStateException cannotConnectUntilDeploymentComplete();

    @Message(id = 3026, value = "%s is not a valid client endpoint type")
    DeploymentException notAValidClientEndpointType(Class<?> type);

    @Message(id = 3027, value = "Class %s was not annotated with @ClientEndpoint or @ServerEndpoint")
    DeploymentException classWasNotAnnotated(Class<?> endpoint);

    @Message(id = 3028, value = "Could not find decoder for type %s on method %s")
    DeploymentException couldNotFindDecoderForType(Class<?> param, Method method);

    @Message(id = 3029, value = "Could not find message parameter on method %s")
    DeploymentException couldNotFindMessageParameter(Method method);

    @Message(id = 3030, value = "Received a text frame however endpoint does not have a method capable of handling it")
    RuntimeException receivedTextFrameButNoMethod();

    @Message(id = 3031, value = "Received a binary frame however endpoint does not have a method capable of handling it")
    RuntimeException receivedBinaryFrameButNoMethod();

    @Message(id = 3033, value = "Method %s has invalid parameters at locations %s. It looks like you may have accidentally used jakarta.ws.rs.PathParam instead of jakarta.websocket.server.PathParam")
    DeploymentException invalidParametersWithWrongAnnotation(Method method, Set<Integer> allParams);

    @Message(id = 3034, value = "Server provided extension %s which was not in client supported extensions %s")
    IOException extensionWasNotPresentInClientHandshake(String e, List<WebSocketExtension> supportedExtensions);

    @Message(id = 3035, value = "Connection timed out")
    IOException connectionTimedOut();

    @Message(id = 3036, value = "SendHandler is null")
    IllegalArgumentException handlerIsNull();

    @Message(id = 3037, value = "Message is null")
    IllegalArgumentException messageInNull();

    @Message(id = 3038, value = "Message of size %s was larger than the maximum of %s")
    IllegalArgumentException messageTooLarge(int size, int max);

    @Message(id = 3039, value = "The container cannot find a suitable constructor to instantiate endpoint of type %s")
    InstantiationException cannotInstantiateEndpoint(Class<?> c);

    @Message(id = 3040, value = "Annotated endpoint instance %s was not of correct type %s")
    IllegalArgumentException endpointNotOfCorrectType(Object instance, Class expected);

    @Message(id = 3041, value = "Annotated endpoint %s does not have a no arg constructor, but is using a custom configurator. The custom configurator must create the instance.")
    InstantiationException endpointDoesNotHaveAppropriateConstructor(Class<?> endpoint);

    @Message(id = 3042, value = "Deployment failed due to invalid programmatically added endpoints")
    RuntimeException deploymentFailedDueToProgramaticErrors();
}
