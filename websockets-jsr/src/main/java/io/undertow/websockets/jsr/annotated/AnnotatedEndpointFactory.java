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

package io.undertow.websockets.jsr.annotated;

import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
import javax.websocket.DeploymentException;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;

import java.io.InputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import io.undertow.servlet.api.InstanceHandle;
import io.undertow.websockets.jsr.Encoding;
import io.undertow.websockets.jsr.EncodingFactory;
import io.undertow.websockets.jsr.JsrWebSocketLogger;
import io.undertow.websockets.jsr.JsrWebSocketMessages;

/**
 * Factory that creates annotated end points.
 *
 * @author Stuart Douglas
 */
public class AnnotatedEndpointFactory {

    private final Class<?> endpointClass;
    private final BoundMethod OnOpen;
    private final BoundMethod OnClose;
    private final BoundMethod OnError;
    private final BoundMethod textMessage;
    private final BoundMethod binaryMessage;
    private final BoundMethod pongMessage;

    private AnnotatedEndpointFactory(final Class<?> endpointClass, final BoundMethod OnOpen, final BoundMethod OnClose, final BoundMethod OnError, final BoundMethod textMessage, final BoundMethod binaryMessage, final BoundMethod pongMessage) {

        this.endpointClass = endpointClass;
        this.OnOpen = OnOpen;
        this.OnClose = OnClose;
        this.OnError = OnError;

        this.textMessage = textMessage;
        this.binaryMessage = binaryMessage;
        this.pongMessage = pongMessage;
    }


    public static AnnotatedEndpointFactory create(final Class<?> endpointClass, final EncodingFactory encodingFactory, final Set<String> paths) throws DeploymentException {
        final Set<Class<? extends Annotation>> found = new HashSet<>();
        BoundMethod onOpen = null;
        BoundMethod onClose = null;
        BoundMethod onError = null;
        BoundMethod textMessage = null;
        BoundMethod binaryMessage = null;
        BoundMethod pongMessage = null;
        Class<?> c = endpointClass;

        do {
            for (final Method method : c.getDeclaredMethods()) {
                if (method.isAnnotationPresent(OnOpen.class)) {
                    if (found.contains(OnOpen.class)) {
                        if(!onOpen.overrides(method)) {
                            throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnOpen.class);
                        } else {
                            continue;
                        }
                    }
                    found.add(OnOpen.class);
                    onOpen = new BoundMethod(method, null, false, 0, new BoundSingleParameter(method, Session.class, true),
                            new BoundSingleParameter(method, EndpointConfig.class, true),
                            createBoundPathParameters(method, paths, endpointClass));
                }
                if (method.isAnnotationPresent(OnClose.class)) {
                    if (found.contains(OnClose.class)) {
                        if(!onClose.overrides(method)) {
                            throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnClose.class);
                        } else {
                            continue;
                        }
                    }
                    found.add(OnClose.class);
                    onClose = new BoundMethod(method, null, false, 0, new BoundSingleParameter(method, Session.class, true),
                            new BoundSingleParameter(method, CloseReason.class, true),
                            createBoundPathParameters(method, paths, endpointClass));
                }
                if (method.isAnnotationPresent(OnError.class)) {
                    if (found.contains(OnError.class)) {
                        if(!onError.overrides(method)) {
                            throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnError.class);
                        } else {
                            continue;
                        }
                    }
                    found.add(OnError.class);
                    onError = new BoundMethod(method, null, false, 0, new BoundSingleParameter(method, Session.class, true),
                            new BoundSingleParameter(method, Throwable.class, false),
                            createBoundPathParameters(method, paths, endpointClass));
                }
                if (method.isAnnotationPresent(OnMessage.class) && ! method.isBridge()) {
                    if(binaryMessage != null && binaryMessage.overrides(method)) {
                        continue;
                    }
                    if(textMessage != null && textMessage.overrides(method)) {
                        continue;
                    }
                    if(pongMessage != null && pongMessage.overrides(method)) {
                        continue;
                    }
                    long maxMessageSize = method.getAnnotation(OnMessage.class).maxMessageSize();
                    boolean messageHandled = false;
                    //this is a bit more complex
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    int booleanLocation = -1;
                    for (int i = 0; i < parameterTypes.length; ++i) {
                        if (hasAnnotation(PathParam.class, method.getParameterAnnotations()[i])) {
                            continue;
                        }

                        final Class<?> param = parameterTypes[i];
                        if(param == boolean.class || param == Boolean.class) {
                            booleanLocation = i;
                        } else if (encodingFactory.canDecodeText(param)) {
                            if (textMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            textMessage = new BoundMethod(method, param, true, maxMessageSize, new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(i, param),
                                    createBoundPathParameters(method, paths, endpointClass));
                            messageHandled = true;
                            break;
                        } else if (encodingFactory.canDecodeBinary(param)) {
                            if (binaryMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            binaryMessage = new BoundMethod(method, param, true, maxMessageSize, new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(i, param),
                                    createBoundPathParameters(method, paths, endpointClass));
                            messageHandled = true;
                            break;
                        } else if (param.equals(byte[].class)) {
                            if (binaryMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            binaryMessage = new BoundMethod(method, byte[].class, false, maxMessageSize, new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(i, byte[].class),
                                    createBoundPathParameters(method, paths, endpointClass));
                            messageHandled = true;
                            break;
                        } else if (param.equals(ByteBuffer.class)) {
                            if (binaryMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            binaryMessage = new BoundMethod(method, ByteBuffer.class, false,
                                    maxMessageSize, new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(i, ByteBuffer.class),
                                    createBoundPathParameters(method, paths, endpointClass));
                            messageHandled = true;
                            break;

                        } else if (param.equals(InputStream.class)) {
                            if (binaryMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            binaryMessage = new BoundMethod(method, InputStream.class, false,
                                    maxMessageSize, new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(i, InputStream.class),
                                    createBoundPathParameters(method, paths, endpointClass));
                            messageHandled = true;
                            break;

                        } else if (param.equals(String.class) && getPathParam(method, i) == null) {
                            if (textMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            textMessage = new BoundMethod(method, String.class, false, maxMessageSize, new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(i, String.class),
                                    createBoundPathParameters(method, paths, endpointClass));
                            messageHandled = true;
                            break;

                        } else if (param.equals(Reader.class) && getPathParam(method, i) == null) {
                            if (textMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            textMessage = new BoundMethod(method, Reader.class, false,
                                    maxMessageSize, new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(method, boolean.class, true),
                                    new BoundSingleParameter(i, Reader.class),
                                    createBoundPathParameters(method, paths, endpointClass));
                            messageHandled = true;
                            break;

                        } else if (param.equals(PongMessage.class)) {
                            if (pongMessage != null) {
                                throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                            }
                            pongMessage = new BoundMethod(method, PongMessage.class, false, maxMessageSize, new BoundSingleParameter(method, Session.class, true),
                                    new BoundSingleParameter(i, PongMessage.class),
                                    createBoundPathParameters(method, paths, endpointClass));
                            messageHandled = true;
                            break;
                        }
                    }
                    if (!messageHandled && booleanLocation != -1) {
                        //so it turns out that the boolean was the message type and not a final fragement indicator
                        if (textMessage != null) {
                            throw JsrWebSocketMessages.MESSAGES.moreThanOneAnnotation(OnMessage.class);
                        }
                        Class<?> boolClass = parameterTypes[booleanLocation];
                        textMessage = new BoundMethod(method, boolClass, true, maxMessageSize, new BoundSingleParameter(method, Session.class, true),
                                new BoundSingleParameter(method, boolean.class, true),
                                new BoundSingleParameter(booleanLocation, boolClass),
                                createBoundPathParameters(method, paths, endpointClass));
                        messageHandled = true;
                    }
                    if (!messageHandled) {
                        throw JsrWebSocketMessages.MESSAGES.couldNotFindMessageParameter(method);
                    }
                }
            }
            c = c.getSuperclass();
        } while (c != Object.class && c != null);
        return new AnnotatedEndpointFactory(endpointClass, onOpen, onClose, onError, textMessage, binaryMessage, pongMessage);
    }

    private static BoundPathParameters createBoundPathParameters(final Method method, Set<String> paths, Class<?> endpointClass) throws DeploymentException {
        return new BoundPathParameters(pathParams(method), method, endpointClass, paths);
    }


    private static String[] pathParams(final Method method) {
        String[] params = new String[method.getParameterTypes().length];
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            PathParam param = getPathParam(method, i);
            if (param != null) {
                params[i] = param.value();
            }
        }
        return params;
    }

    private static PathParam getPathParam(final Method method, final int parameter) {
        for (final Annotation annotation : method.getParameterAnnotations()[parameter]) {
            if (annotation.annotationType().equals(PathParam.class)) {
                return (PathParam) annotation;
            }
        }
        return null;
    }

    private static boolean hasAnnotation(Class<? extends Annotation> annotationType, Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(annotationType)) {
                return true;
            }
        }

        return false;
    }

    public AnnotatedEndpoint createInstance(InstanceHandle<?> endpointInstance) {
        if(!endpointClass.isInstance(endpointInstance.getInstance())) {
            throw JsrWebSocketMessages.MESSAGES.endpointNotOfCorrectType(endpointInstance, endpointClass);
        }
        return new AnnotatedEndpoint(endpointInstance, OnOpen, OnClose, OnError, textMessage, binaryMessage, pongMessage);
    }

    /**
     * represents a parameter binding
     */
    private static class BoundSingleParameter implements BoundParameter {

        private final int position;
        private final Class<?> type;

        BoundSingleParameter(int position, final Class<?> type) {
            this.position = position;
            this.type = type;
        }

        BoundSingleParameter(final Method method, final Class<?> type, final boolean optional) {
            this.type = type;
            int pos = -1;
            for (int i = 0; i < method.getParameterTypes().length; ++i) {
                boolean pathParam = false;
                for (Annotation annotation : method.getParameterAnnotations()[i]) {
                    if (annotation.annotationType().equals(PathParam.class)) {
                        pathParam = true;
                        break;
                    }
                }
                if (pathParam) {
                    continue;
                }
                if (method.getParameterTypes()[i].equals(type)) {
                    if (pos != -1) {
                        throw JsrWebSocketMessages.MESSAGES.moreThanOneParameterOfType(type, method);
                    }
                    pos = i;
                }
            }
            if (pos != -1) {
                position = pos;
            } else if (optional) {
                position = -1;
            } else {
                throw JsrWebSocketMessages.MESSAGES.parameterNotFound(type, method);
            }
        }

        public Set<Integer> positions() {
            if (position == -1) {
                return Collections.emptySet();
            }
            return Collections.singleton(position);
        }


        public void populate(final Object[] params, final Map<Class<?>, Object> value) {
            if (position == -1) {
                return;
            }
            params[position] = value.get(type);
        }

        @Override
        public Class<?> getType() {
            return type;
        }
    }

    /**
     * represents a parameter binding
     */
    private static class BoundPathParameters implements BoundParameter {

        private final Class<?> endpointClass;
        private final Set<String> paths;
        private final String[] positions;
        private final Encoding[] encoders;
        private final Class[] types;

        BoundPathParameters(final String[] positions, final Method method, Class<?> endpointClass, Set<String> paths) throws DeploymentException {
            this.positions = positions;
            this.endpointClass = endpointClass;
            this.paths = paths;
            this.encoders = new Encoding[positions.length];
            this.types = new Class[positions.length];
            for (int i = 0; i < positions.length; ++i) {
                Class type = method.getParameterTypes()[i];
                Annotation[] annotations = method.getParameterAnnotations()[i];
                for(int j = 0; j < annotations.length; ++j) {
                    if(annotations[j] instanceof PathParam) {
                        PathParam param = (PathParam) annotations[j];
                        if(!paths.contains(param.value())) {
                            JsrWebSocketLogger.ROOT_LOGGER.pathTemplateNotFound(endpointClass, param, method, paths);
                        }
                    }
                }
                if (positions[i] == null || type == null || type == String.class) {
                    continue;
                }
                if (EncodingFactory.DEFAULT.canEncodeText(type)) {
                    encoders[i] = EncodingFactory.DEFAULT.createEncoding(EmptyEndpointConfig.INSTANCE);
                    types[i] = type;

                } else {
                    throw JsrWebSocketMessages.MESSAGES.couldNotFindDecoderForType(type, method);
                }
            }
        }

        public Set<Integer> positions() {
            HashSet<Integer> ret = new HashSet<>();
            for (int i = 0; i < positions.length; ++i) {
                if (positions[i] != null) {
                    ret.add(i);
                }
            }
            return ret;
        }


        public void populate(final Object[] params, final Map<Class<?>, Object> value) throws DecodeException {
            final Map<String, String> data = (Map<String, String>) value.get(Map.class);
            for (int i = 0; i < positions.length; ++i) {
                String name = positions[i];
                if (name != null) {
                    Encoding encoding = encoders[i];
                    if (encoding == null) {
                        params[i] = data.get(name);
                    } else {
                        params[i] = encoding.decodeText(types[i], data.get(name));
                    }
                }
            }
        }

        @Override
        public Class<?> getType() {
            return Map.class;
        }
    }
}
