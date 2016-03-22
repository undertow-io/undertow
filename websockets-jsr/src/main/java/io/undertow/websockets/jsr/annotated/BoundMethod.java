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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.websocket.DeploymentException;

import io.undertow.websockets.jsr.JsrWebSocketMessages;

/**
 * A method with bound parameters
 */
final class BoundMethod {

    private final Method method;
    private final List<BoundParameter> parameters = new ArrayList<>();
    private final Set<Class> paramTypes = new HashSet<>();
    private final Class<?> messageType;
    private final boolean decoderRequired;
    private final long maxMessageSize;

    BoundMethod(final Method method, final Class<?> messageType, final boolean decoderRequired, long maxMessageSize, BoundParameter... params) throws DeploymentException {
        this.method = method;
        this.messageType = messageType;
        this.decoderRequired = decoderRequired;
        this.maxMessageSize = maxMessageSize;
        final Set<Integer> allParams = new HashSet<>();
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            allParams.add(i);
            paramTypes.add(method.getParameterTypes()[i]);
        }
        for (BoundParameter param : params) {
            parameters.add(param);
            allParams.removeAll(param.positions());
        }
        if (!allParams.isEmpty()) {
            //first check to see if the user has accidentally used the wrong PathParam annotation
            //and if so throw a more informative error message
            boolean wrongAnnotation = false;
            for (int i = 0; i < method.getParameterAnnotations().length; ++i) {
                for (int j = 0; j < method.getParameterAnnotations()[i].length; ++j) {
                    Annotation annotation = method.getParameterAnnotations()[i][j];
                    if (annotation.annotationType().getName().equals("javax.ws.rs.PathParam")) {
                        wrongAnnotation = true;
                    }
                }
            }
            if (wrongAnnotation) {
                throw JsrWebSocketMessages.MESSAGES.invalidParametersWithWrongAnnotation(method, allParams);
            } else {
                throw JsrWebSocketMessages.MESSAGES.invalidParameters(method, allParams);
            }
        }
        method.setAccessible(true);
    }

    public Object invoke(final Object instance, final Map<Class<?>, Object> values) throws Exception {
        final Object[] params = new Object[method.getParameterTypes().length];
        for (BoundParameter param : parameters) {
            param.populate(params, values);
        }
        try {
            return method.invoke(instance, params);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            if(e.getCause() instanceof Exception) {
                throw (Exception)e.getCause();
            } else {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    public boolean hasParameterType(final Class<?> type) {
        return paramTypes.contains(type);
    }

    public Class<?> getMessageType() {
        return messageType;
    }

    public boolean isDecoderRequired() {
        return decoderRequired;
    }

    public long getMaxMessageSize() {
        return maxMessageSize;
    }

    public boolean overrides(Method method) {
        if(!method.getName().equals(this.method.getName())) {
            return false;
        }
        if(!method.getReturnType().isAssignableFrom(this.method.getReturnType())) {
            return false;
        }
        if(method.getParameterTypes().length != this.method.getParameterTypes().length) {
            return false;
        }
        for(int i = 0; i < method.getParameterTypes().length; ++i) {
            if(method.getParameterTypes()[i] != this.method.getParameterTypes()[i]) {
                return false;
            }
        }
        return true;
    }
}
