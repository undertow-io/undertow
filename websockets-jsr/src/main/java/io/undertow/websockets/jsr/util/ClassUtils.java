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
package io.undertow.websockets.jsr.util;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.MessageHandler;

import io.undertow.websockets.jsr.JsrWebSocketMessages;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class ClassUtils {
    private ClassUtils() {
    }

    /**
     * Returns a map of all supported message types by the given handler class.
     * The key of the map is the supported message type; the value indicates
     * whether it is a partial message handler or not.
     *
     * @return a map of all supported message types by the given handler class.
     */
    public static Map<Class<?>, Boolean> getHandlerTypes(Class<? extends MessageHandler> clazz) {
        Map<Class<?>, Boolean> types = new IdentityHashMap<Class<?>, Boolean>(2);
        for (Class<?> c = clazz; c != Object.class; c = c.getSuperclass()) {
            exampleGenericInterfaces(types, c);
        }
        if (types.isEmpty()) {
            throw JsrWebSocketMessages.MESSAGES.unknownHandlerType(clazz);
        }
        return types;
    }

    private static void exampleGenericInterfaces(Map<Class<?>, Boolean> types, Class<?> c) {
        for (Type type : c.getGenericInterfaces()) {
            if (type instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) type;
                Type rawType = pt.getRawType();
                if (rawType == MessageHandler.Whole.class) {
                    Type messageType = pt.getActualTypeArguments()[0];
                    types.put((Class<?>) messageType, Boolean.FALSE);
                } else if (rawType == MessageHandler.Partial.class) {
                    Type messageType = pt.getActualTypeArguments()[0];
                    types.put((Class<?>) messageType, Boolean.TRUE);
                } else if(rawType instanceof Class) {
                    Class rawClass = (Class) rawType;
                    if(rawClass.getGenericInterfaces() != null) {
                        exampleGenericInterfaces(types, rawClass);
                    }
                }
            } else if(type instanceof Class) {
                exampleGenericInterfaces(types, (Class)type);
            }
        }
    }

    /**
     * Returns the Object type for which the {@link Encoder} can be used.
     */
    public static Class<?> getEncoderType(Class<? extends Encoder> clazz) {
        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if ("encode".equals(m.getName()) && !m.isBridge()) {
                return m.getParameterTypes()[0];
            }
        }
        throw JsrWebSocketMessages.MESSAGES.unknownEncoderType(clazz);
    }

    /**
     * Returns the Object type for which the {@link Encoder} can be used.
     */
    public static Class<?> getDecoderType(Class<? extends Decoder> clazz) {
        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if ("decode".equals(m.getName()) && !m.isBridge()) {
                return m.getReturnType();
            }
        }
        throw JsrWebSocketMessages.MESSAGES.couldNotDetermineDecoderTypeFor(clazz);
    }

}
