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

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.MessageHandler;
import java.lang.reflect.Method;

import io.undertow.websockets.jsr.JsrWebSocketMessages;

/**
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class ClassUtils {
    private ClassUtils() {}

    /**
     * Returns the frame type the {@link MessageHandler} handles.
     */
    public static Class<?> getHandlerType(Class<? extends MessageHandler> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m: methods) {
            if ("onMessage".equals(m.getName())) {
               return m.getParameterTypes()[0];
            }
        }
        throw JsrWebSocketMessages.MESSAGES.unkownHandlerType(clazz);
    }

    /**
     * Returns the Object type for which the {@link Encoder} can be used.
     */
    public static Class<?> getEncoderType(Class<? extends Encoder> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m: methods) {
            if ("encode".equals(m.getName())) {
                return m.getParameterTypes()[0];
            }
        }
        throw JsrWebSocketMessages.MESSAGES.unknownEncoderType(clazz);
    }

    /**
     * Returns the Object type for which the {@link Encoder} can be used.
     */
    public static Class<?> getDecoderType(Class<? extends Decoder> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m: methods) {
            if ("decode".equals(m.getName())) {
                return m.getReturnType();
            }
        }
        throw JsrWebSocketMessages.MESSAGES.couldNotDetermineDecoderTypeFor(clazz);
    }
}
