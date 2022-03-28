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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;

import io.undertow.servlet.api.InstanceHandle;

/**
 * Manages all encoders and decoders for an endpoint instance
 *
 * @author Stuart Douglas
 */
public class Encoding implements Closeable {


    private final Map<Class<?>, List<InstanceHandle<? extends Encoder>>> binaryEncoders;
    private final Map<Class<?>, List<InstanceHandle<? extends Decoder>>> binaryDecoders;
    private final Map<Class<?>, List<InstanceHandle<? extends Encoder>>> textEncoders;
    private final Map<Class<?>, List<InstanceHandle<? extends Decoder>>> textDecoders;

    public Encoding(final Map<Class<?>, List<InstanceHandle<? extends Encoder>>> binaryEncoders, final Map<Class<?>, List<InstanceHandle<? extends Decoder>>> binaryDecoders, final Map<Class<?>, List<InstanceHandle<? extends Encoder>>> textEncoders, final Map<Class<?>, List<InstanceHandle<? extends Decoder>>> textDecoders) {
        this.binaryEncoders = binaryEncoders;
        this.binaryDecoders = binaryDecoders;
        this.textEncoders = textEncoders;
        this.textDecoders = textDecoders;
    }


    public boolean canEncodeText(final Class<?> type) {
        if(textEncoders.containsKey(type)) {
            return true;
        }
        for(Class<?> key : textEncoders.keySet()) {
            if(key.isAssignableFrom(type)) {
                return true;
            }
        }
        if (EncodingFactory.isPrimitiveOrBoxed(type)) {
            Class<?> primType = boxedType(type);
            return !binaryEncoders.containsKey(primType) && !binaryEncoders.containsKey(Object.class); //don't use a built in coding if a user supplied binary one is present
        }
        return false;
    }


    public boolean canDecodeText(final Class<?> type) {
        if(textDecoders.containsKey(type)) {
            return true;
        }
        if (EncodingFactory.isPrimitiveOrBoxed(type)) {
            Class<?> primType = boxedType(type);
            return !binaryDecoders.containsKey(primType) && !binaryEncoders.containsKey(Object.class); //don't use a built in coding if a user supplied binary one is present
        }
        return false;
    }


    public boolean canEncodeBinary(final Class<?> type) {
        if(binaryEncoders.containsKey(type)) {
            return true;
        }

        for(Class<?> key : binaryEncoders.keySet()) {
            if(key.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }


    public boolean canDecodeBinary(final Class<?> type) {
        return binaryDecoders.containsKey(type);
    }


    public Object decodeText(final Class<?> targetType, final String message) throws DecodeException {
        if (EncodingFactory.isPrimitiveOrBoxed(targetType)) {
            return decodePrimitive(targetType, message);
        }
        List<InstanceHandle<? extends Decoder>> decoders = textDecoders.get(targetType);
        if (decoders != null) {
            for (InstanceHandle<? extends Decoder> decoderHandle : decoders) {
                Decoder decoder = decoderHandle.getInstance();
                if (decoder instanceof Decoder.Text) {
                    if (((Decoder.Text) decoder).willDecode(message)) {
                        return ((Decoder.Text) decoder).decode(message);
                    }
                } else {
                    try {
                        return ((Decoder.TextStream) decoder).decode(new StringReader(message));
                    } catch (IOException e) {
                        throw new DecodeException(message, "Could not decode string", e);
                    }
                }
            }
        }
        throw new DecodeException(message, "Could not decode string");
    }

    private Object decodePrimitive(final Class<?> targetType, final String message) throws DecodeException {
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(message);
        } else if (targetType == Character.class || targetType == char.class) {
            return message.charAt(0);
        } else if (targetType == Byte.class || targetType == byte.class) {
            return Byte.valueOf(message);
        } else if (targetType == Short.class || targetType == short.class) {
            return Short.valueOf(message);
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(message);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(message);
        } else if (targetType == Float.class || targetType == float.class) {
            return Float.valueOf(message);
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(message);
        }
        return null; // impossible
    }

    public Object decodeBinary(final Class<?> targetType, final byte[] bytes) throws DecodeException {
        List<InstanceHandle<? extends Decoder>> decoders = binaryDecoders.get(targetType);
        if (decoders != null) {
            for (InstanceHandle<? extends Decoder> decoderHandle : decoders) {
                Decoder decoder = decoderHandle.getInstance();
                if (decoder instanceof Decoder.Binary) {
                    if (((Decoder.Binary) decoder).willDecode(ByteBuffer.wrap(bytes))) {
                        return ((Decoder.Binary) decoder).decode(ByteBuffer.wrap(bytes));
                    }
                } else {
                    try {
                        return ((Decoder.BinaryStream) decoder).decode(new ByteArrayInputStream(bytes));
                    } catch (IOException e) {
                        throw new DecodeException(ByteBuffer.wrap(bytes), "Could not decode binary", e);
                    }
                }
            }
        }
        throw new DecodeException(ByteBuffer.wrap(bytes), "Could not decode binary");
    }

    public String encodeText(final Object o) throws EncodeException {
        List<InstanceHandle<? extends Encoder>> encoders = textEncoders.get(o.getClass());
        if(encoders == null) {
            for(Map.Entry<Class<?>, List<InstanceHandle<? extends Encoder>>> entry : textEncoders.entrySet()) {
                if(entry.getKey().isAssignableFrom(o.getClass())) {
                    encoders = entry.getValue();
                    break;
                }
            }
        }
        if (encoders != null) {
            for (InstanceHandle<? extends Encoder> decoderHandle : encoders) {
                Encoder decoder = decoderHandle.getInstance();
                if (decoder instanceof Encoder.Text) {
                    return ((Encoder.Text) decoder).encode(o);
                } else {
                    try {
                        StringWriter out = new StringWriter();
                        ((Encoder.TextStream) decoder).encode(o, out);
                        return out.toString();
                    } catch (IOException e) {
                        throw new EncodeException(o, "Could not encode text", e);
                    }
                }
            }
        }

        if (EncodingFactory.isPrimitiveOrBoxed(o.getClass())) {
            return o.toString();
        }
        throw new EncodeException(o, "Could not encode text");
    }

    public ByteBuffer encodeBinary(final Object o) throws EncodeException {
        List<InstanceHandle<? extends Encoder>> encoders = binaryEncoders.get(o.getClass());

        if(encoders == null) {
            for(Map.Entry<Class<?>, List<InstanceHandle<? extends Encoder>>> entry : binaryEncoders.entrySet()) {
                if(entry.getKey().isAssignableFrom(o.getClass())) {
                    encoders = entry.getValue();
                    break;
                }
            }
        }
        if (encoders != null) {
            for (InstanceHandle<? extends Encoder> decoderHandle : encoders) {
                Encoder decoder = decoderHandle.getInstance();
                if (decoder instanceof Encoder.Binary) {
                    return ((Encoder.Binary) decoder).encode(o);
                } else {
                    try {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ((Encoder.BinaryStream) decoder).encode(o, out);
                        return ByteBuffer.wrap(out.toByteArray());
                    } catch (IOException e) {
                        throw new EncodeException(o, "Could not encode binary", e);
                    }
                }
            }
        }
        throw new EncodeException(o, "Could not encode binary");
    }

    @Override
    public void close() {
        for (Map.Entry<Class<?>, List<InstanceHandle<? extends Decoder>>> entry : binaryDecoders.entrySet()) {
            for (InstanceHandle<? extends Decoder> val : entry.getValue()) {
                val.getInstance().destroy();
                val.release();
            }
        }
        for (Map.Entry<Class<?>, List<InstanceHandle<? extends Decoder>>> entry : textDecoders.entrySet()) {
            for (InstanceHandle<? extends Decoder> val : entry.getValue()) {
                val.getInstance().destroy();
                val.release();
            }
        }
        for (Map.Entry<Class<?>, List<InstanceHandle<? extends Encoder>>> entry : binaryEncoders.entrySet()) {
            for (InstanceHandle<? extends Encoder> val : entry.getValue()) {
                val.getInstance().destroy();
                val.release();
            }
        }
        for (Map.Entry<Class<?>, List<InstanceHandle<? extends Encoder>>> entry : textEncoders.entrySet()) {
            for (InstanceHandle<? extends Encoder> val : entry.getValue()) {
                val.getInstance().destroy();
                val.release();
            }
        }
    }

    private static Class<?> boxedType(final Class<?> targetType) {
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.class;
        } else if (targetType == Character.class || targetType == char.class) {
            return Character.class;
        } else if (targetType == Byte.class || targetType == byte.class) {
            return Byte.class;
        } else if (targetType == Short.class || targetType == short.class) {
            return Short.class;
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.class;
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.class;
        } else if (targetType == Float.class || targetType == float.class) {
            return Float.class;
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.class;
        }
        return targetType;
    }
}
