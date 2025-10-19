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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.websocket.Decoder;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;

import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;

/**
 * Factory class that produces encoding instances for an endpoint. This also provides static
 * methods about the capabilities of encoders.
 * <p>
 * These classes also perform implicit encodings for java primitives
 *
 * @author Stuart Douglas
 */
public class EncodingFactory {

    /**
     * An encoding factory that can deal with primitive types.
     */
    public static final EncodingFactory DEFAULT = new EncodingFactory(Collections.EMPTY_MAP, Collections.EMPTY_MAP, Collections.EMPTY_MAP, Collections.EMPTY_MAP);

    private final Map<Class<?>, List<InstanceFactory<? extends Encoder>>> binaryEncoders;
    private final Map<Class<?>, List<InstanceFactory<? extends Decoder>>> binaryDecoders;
    private final Map<Class<?>, List<InstanceFactory<? extends Encoder>>> textEncoders;
    private final Map<Class<?>, List<InstanceFactory<? extends Decoder>>> textDecoders;

    public EncodingFactory(final Map<Class<?>, List<InstanceFactory<? extends Encoder>>> binaryEncoders, final Map<Class<?>, List<InstanceFactory<? extends Decoder>>> binaryDecoders, final Map<Class<?>, List<InstanceFactory<? extends Encoder>>> textEncoders, final Map<Class<?>, List<InstanceFactory<? extends Decoder>>> textDecoders) {
        this.binaryEncoders = binaryEncoders;
        this.binaryDecoders = binaryDecoders;
        this.textEncoders = textEncoders;
        this.textDecoders = textDecoders;
    }

    public boolean canEncodeText(final Class<?> type) {
        if (isPrimitiveOrBoxed(type)) {
            return true;
        }
        return textEncoders.containsKey(type);
    }


    public boolean canDecodeText(final Class<?> type) {
        if (isPrimitiveOrBoxed(type)) {
            return true;
        }
        return textDecoders.containsKey(type);
    }


    public boolean canEncodeBinary(final Class<?> type) {
        return binaryEncoders.containsKey(type);
    }


    public boolean canDecodeBinary(final Class<?> type) {
        return binaryDecoders.containsKey(type);
    }

    public Encoding createEncoding(final EndpointConfig endpointConfig) {
        try {
            Map<Class<?>, List<InstanceHandle<? extends Encoder>>> binaryEncoders = this.binaryEncoders.isEmpty() ? Collections.<Class<?>, List<InstanceHandle<? extends Encoder>>>emptyMap() : new HashMap<>();
            Map<Class<?>, List<InstanceHandle<? extends Decoder>>> binaryDecoders = this.binaryDecoders.isEmpty() ? Collections.<Class<?>, List<InstanceHandle<? extends Decoder>>>emptyMap() : new HashMap<>();
            Map<Class<?>, List<InstanceHandle<? extends Encoder>>> textEncoders = this.textEncoders.isEmpty() ? Collections.<Class<?>, List<InstanceHandle<? extends Encoder>>>emptyMap() : new HashMap<>();
            Map<Class<?>, List<InstanceHandle<? extends Decoder>>> textDecoders = this.textDecoders.isEmpty() ? Collections.<Class<?>, List<InstanceHandle<? extends Decoder>>>emptyMap() : new HashMap<>();

            for (Map.Entry<Class<?>, List<InstanceFactory<? extends Encoder>>> entry : this.binaryEncoders.entrySet()) {
                final List<InstanceHandle<? extends Encoder>> val = new ArrayList<>(entry.getValue().size());
                binaryEncoders.put(entry.getKey(), val);
                for (InstanceFactory<? extends Encoder> factory : entry.getValue()) {
                    InstanceHandle<? extends Encoder> instance = factory.createInstance();
                    instance.getInstance().init(endpointConfig);
                    val.add(instance);
                }
            }
            for (Map.Entry<Class<?>, List<InstanceFactory<? extends Decoder>>> entry : this.binaryDecoders.entrySet()) {
                final List<InstanceHandle<? extends Decoder>> val = new ArrayList<>(entry.getValue().size());
                binaryDecoders.put(entry.getKey(), val);
                for (InstanceFactory<? extends Decoder> factory : entry.getValue()) {
                    InstanceHandle<? extends Decoder> instance = factory.createInstance();
                    instance.getInstance().init(endpointConfig);
                    val.add(instance);
                }
            }
            for (Map.Entry<Class<?>, List<InstanceFactory<? extends Encoder>>> entry : this.textEncoders.entrySet()) {
                final List<InstanceHandle<? extends Encoder>> val = new ArrayList<>(entry.getValue().size());
                textEncoders.put(entry.getKey(), val);
                for (InstanceFactory<? extends Encoder> factory : entry.getValue()) {
                    InstanceHandle<? extends Encoder> instance = factory.createInstance();
                    instance.getInstance().init(endpointConfig);
                    val.add(instance);
                }
            }
            for (Map.Entry<Class<?>, List<InstanceFactory<? extends Decoder>>> entry : this.textDecoders.entrySet()) {
                final List<InstanceHandle<? extends Decoder>> val = new ArrayList<>(entry.getValue().size());
                textDecoders.put(entry.getKey(), val);
                for (InstanceFactory<? extends Decoder> factory : entry.getValue()) {
                    InstanceHandle<? extends Decoder> instance = factory.createInstance();
                    instance.getInstance().init(endpointConfig);
                    val.add(instance);
                }
            }
            return new Encoding(binaryEncoders, binaryDecoders, textEncoders, textDecoders);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    public static EncodingFactory createFactory(final ClassIntrospecter classIntrospecter, final Class<? extends Decoder>[] decoders, final Class<? extends Encoder>[] encoders) throws DeploymentException {
        return createFactory(classIntrospecter, Arrays.asList(decoders), Arrays.asList(encoders));
    }

    public static EncodingFactory createFactory(final ClassIntrospecter classIntrospecter, final List<Class<? extends Decoder>> decoders, final List<Class<? extends Encoder>> encoders) throws DeploymentException {
        final Map<Class<?>, List<InstanceFactory<? extends Encoder>>> binaryEncoders = new HashMap<>();
        final Map<Class<?>, List<InstanceFactory<? extends Decoder>>> binaryDecoders = new HashMap<>();
        final Map<Class<?>, List<InstanceFactory<? extends Encoder>>> textEncoders = new HashMap<>();
        final Map<Class<?>, List<InstanceFactory<? extends Decoder>>> textDecoders = new HashMap<>();

        for (Class<? extends Decoder> decoder : decoders) {
            if (Decoder.Binary.class.isAssignableFrom(decoder)) {
                try {
                    Method method = decoder.getMethod("decode", ByteBuffer.class);
                    final Class<?> type = resolveReturnType(method, decoder);
                    List<InstanceFactory<? extends Decoder>> list = binaryDecoders.get(type);
                    if (list == null) {
                        binaryDecoders.put(type, list = new ArrayList<>());
                    }
                    list.add(classIntrospecter.createInstanceFactory(decoder));
                } catch (NoSuchMethodException e) {
                    throw JsrWebSocketMessages.MESSAGES.couldNotDetermineTypeOfDecodeMethodForClass(decoder, e);
                }
            } else if (Decoder.BinaryStream.class.isAssignableFrom(decoder)) {
                try {
                    Method method = decoder.getMethod("decode", InputStream.class);
                    final Class<?> type = resolveReturnType(method, decoder);
                    List<InstanceFactory<? extends Decoder>> list = binaryDecoders.get(type);
                    if (list == null) {
                        binaryDecoders.put(type, list = new ArrayList<>());
                    }
                    list.add(classIntrospecter.createInstanceFactory(decoder));
                } catch (NoSuchMethodException e) {
                    throw JsrWebSocketMessages.MESSAGES.couldNotDetermineTypeOfDecodeMethodForClass(decoder, e);
                }
            } else if (Decoder.Text.class.isAssignableFrom(decoder)) {
                try {
                    Method method = decoder.getMethod("decode", String.class);
                    final Class<?> type = resolveReturnType(method, decoder);
                    List<InstanceFactory<? extends Decoder>> list = textDecoders.get(type);
                    if (list == null) {
                        textDecoders.put(type, list = new ArrayList<>());
                    }
                    list.add(classIntrospecter.createInstanceFactory(decoder));
                } catch (NoSuchMethodException e) {
                    throw JsrWebSocketMessages.MESSAGES.couldNotDetermineTypeOfDecodeMethodForClass(decoder, e);
                }
            } else if (Decoder.TextStream.class.isAssignableFrom(decoder)) {
                try {
                    Method method = decoder.getMethod("decode", Reader.class);
                    final Class<?> type = resolveReturnType(method, decoder);
                    List<InstanceFactory<? extends Decoder>> list = textDecoders.get(type);
                    if (list == null) {
                        textDecoders.put(type, list = new ArrayList<>());
                    }
                    list.add(createInstanceFactory(classIntrospecter, decoder));
                } catch (NoSuchMethodException e) {
                    throw JsrWebSocketMessages.MESSAGES.couldNotDetermineTypeOfDecodeMethodForClass(decoder, e);
                }
            } else {
                throw JsrWebSocketMessages.MESSAGES.didNotImplementKnownDecoderSubclass(decoder);
            }
        }

        for (Class<? extends Encoder> encoder : encoders) {
            if (Encoder.Binary.class.isAssignableFrom(encoder)) {
                final Class<?> type = findEncodeMethod(encoder, ByteBuffer.class);
                List<InstanceFactory<? extends Encoder>> list = binaryEncoders.get(type);
                if (list == null) {
                    binaryEncoders.put(type, list = new ArrayList<>());
                }
                list.add(createInstanceFactory(classIntrospecter, encoder));
            } else if (Encoder.BinaryStream.class.isAssignableFrom(encoder)) {
                final Class<?> type = findEncodeMethod(encoder, void.class, OutputStream.class);
                List<InstanceFactory<? extends Encoder>> list = binaryEncoders.get(type);
                if (list == null) {
                    binaryEncoders.put(type, list = new ArrayList<>());
                }
                list.add(createInstanceFactory(classIntrospecter, encoder));
            } else if (Encoder.Text.class.isAssignableFrom(encoder)) {
                final Class<?> type = findEncodeMethod(encoder, String.class);
                List<InstanceFactory<? extends Encoder>> list = textEncoders.get(type);
                if (list == null) {
                    textEncoders.put(type, list = new ArrayList<>());
                }
                list.add(createInstanceFactory(classIntrospecter, encoder));
            } else if (Encoder.TextStream.class.isAssignableFrom(encoder)) {
                final Class<?> type = findEncodeMethod(encoder, void.class, Writer.class);
                List<InstanceFactory<? extends Encoder>> list = textEncoders.get(type);
                if (list == null) {
                    textEncoders.put(type, list = new ArrayList<>());
                }
                list.add(createInstanceFactory(classIntrospecter, encoder));
            }
        }
        return new EncodingFactory(binaryEncoders, binaryDecoders, textEncoders, textDecoders);
    }

    private static Class<?> resolveReturnType(Method method, Class<? extends Decoder> decoder) {
        Type genericReturnType = method.getGenericReturnType();
        if (genericReturnType instanceof Class) {
            return (Class<?>) genericReturnType;
        } else if (genericReturnType instanceof TypeVariable) {
            TypeVariable type = ((TypeVariable) genericReturnType);
            List<Class> classes = new ArrayList<>();
            Class c = decoder;
            while (c != method.getDeclaringClass() && c != null) {
                classes.add(c);
                c = c.getSuperclass();
            }
            Collections.reverse(classes);

            String currentName = type.getName();
            int currentPos = -1;
            for (Class clz : classes) {
                for (int i = 0; i < clz.getSuperclass().getTypeParameters().length; ++i) {
                    TypeVariable<? extends Class<?>> param = clz.getSuperclass().getTypeParameters()[i];
                    if (param.getName().equals(currentName)) {
                        currentPos = i;
                        break;
                    }
                }
                Type gs = clz.getGenericSuperclass();
                if (gs instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) gs;
                    Type at = pt.getActualTypeArguments()[currentPos];
                    if (at instanceof Class) {
                        return (Class<?>) at;
                    } else if (at instanceof TypeVariable) {
                        TypeVariable tv = (TypeVariable) at;
                        currentName = tv.getName();
                    }
                }
            }
            //todo: should we throw an exception here? It should never actually be reached
            return method.getReturnType();
        } else {
            return method.getReturnType();
        }
    }

    private static <T> InstanceFactory<? extends T> createInstanceFactory(final ClassIntrospecter classIntrospecter, final Class<? extends T> decoder) throws DeploymentException {
        try {
            return classIntrospecter.createInstanceFactory(decoder);
        } catch (NoSuchMethodException e) {
            throw JsrWebSocketMessages.MESSAGES.classDoesNotHaveDefaultConstructor(decoder, e);
        }
    }

    private static Class<?> findEncodeMethod(final Class<? extends Encoder> encoder, final Class<?> returnType, Class<?>... otherParameters) throws DeploymentException {
        for (Method method : encoder.getMethods()) {
            if (method.getName().equals("encode") && !method.isBridge() &&
                    method.getParameterCount() == 1 + otherParameters.length &&
                    method.getReturnType() == returnType) {
                boolean ok = true;
                for (int i = 1; i < method.getParameterCount(); ++i) {
                    if (method.getParameterTypes()[i] != otherParameters[i - 1]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return method.getParameterTypes()[0];
                }
            }
        }
        throw JsrWebSocketMessages.MESSAGES.couldNotDetermineTypeOfEncodeMethodForClass(encoder);
    }


    static boolean isPrimitiveOrBoxed(final Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == Boolean.class ||
                clazz == Byte.class ||
                clazz == Character.class ||
                clazz == Short.class ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Float.class ||
                clazz == Double.class;//we don't care about void
    }
}
