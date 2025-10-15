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
package io.undertow.websockets.jsr.util;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import jakarta.websocket.Decoder;
import jakarta.websocket.Encoder;
import jakarta.websocket.MessageHandler;

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
        Map<Class<?>, Boolean> types = new IdentityHashMap<>(2);
        for (Class<?> c = clazz; c != Object.class; c = c.getSuperclass()) {
            exampleGenericInterfaces(types, c, clazz);
        }
        if (types.isEmpty()) {
            throw JsrWebSocketMessages.MESSAGES.unknownHandlerType(clazz);
        }
        return types;
    }

    private static void exampleGenericInterfaces(Map<Class<?>, Boolean> types, Class<?> c, Class actualClass) {
        for (Type type : c.getGenericInterfaces()) {
            if (type instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) type;
                Type rawType = pt.getRawType();
                if (rawType == MessageHandler.Whole.class) {
                    Type messageType = pt.getActualTypeArguments()[0];
                    types.put(resolvePotentialTypeVariable(messageType, c, actualClass), Boolean.FALSE);
                } else if (rawType == MessageHandler.Partial.class) {
                    Type messageType = pt.getActualTypeArguments()[0];
                    types.put(resolvePotentialTypeVariable(messageType, c, actualClass), Boolean.TRUE);
                } else if(rawType instanceof Class) {
                    Class rawClass = (Class) rawType;
                    if(rawClass.getGenericInterfaces() != null) {
                        exampleGenericInterfaces(types, rawClass, actualClass);
                    }
                }
            } else if(type instanceof Class) {
                exampleGenericInterfaces(types, (Class)type, actualClass);
            }
        }
    }

    private static Class<?> resolvePotentialTypeVariable(Type messageType, Class<?> c, Class actualClass) {
        if(messageType instanceof Class) {
            return (Class<?>) messageType;
        } else if(messageType instanceof TypeVariable) {
            Type var = messageType;
            int tvpos = 0;
            List<Class> parents = new ArrayList<>();
            Class i = actualClass;
            while (i != c) {
                parents.add(i);
                i = i.getSuperclass();
            }
            Collections.reverse(parents);
            for(Class ptype : parents) {
                Type type = ptype.getGenericSuperclass();
                if(!(type instanceof ParameterizedType)) {
                    throw JsrWebSocketMessages.MESSAGES.unknownHandlerType(actualClass);
                }
                ParameterizedType pt = (ParameterizedType) type;
                if(tvpos == -1) {

                    TypeVariable[] typeParameters = ((Class) pt.getRawType()).getTypeParameters();
                    for(int j = 0; j <  typeParameters.length; ++j) {
                        TypeVariable tp = typeParameters[j];
                        if(tp.getName().equals(((TypeVariable)var).getName())) {
                            tvpos = j;
                            break;
                        }
                    }
                }
                var = pt.getActualTypeArguments()[tvpos];
                if(var instanceof Class) {
                    return (Class<?>) var;
                }
                tvpos = -1;
            }
            return (Class<?>) var;
        } else {
            throw JsrWebSocketMessages.MESSAGES.unknownHandlerType(actualClass);
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
