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
package io.undertow.websockets.jsr.test;

import io.undertow.testutils.category.UnitTest;
import io.undertow.websockets.jsr.util.ClassUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@Category(UnitTest.class)
public class ClassUtilsTest {

    @Test
    public void testExtractHandlerType() {

        Map<Class<?>, Boolean>  types = ClassUtils.getHandlerTypes(FinalIm.class);
        Assert.assertEquals(1, types.size());
        Assert.assertTrue(types.containsKey(ByteBuffer.class));

        types = ClassUtils.getHandlerTypes(ByteBufferFromSuperClassEncoder.class);
        Assert.assertEquals(1, types.size());
        Assert.assertTrue(types.containsKey(ByteBuffer.class));

        types = ClassUtils.getHandlerTypes(MessageHandlerImpl.class);
        Assert.assertEquals(1, types.size());
        Assert.assertTrue(types.containsKey(ByteBuffer.class));
        Assert.assertFalse(types.get(ByteBuffer.class));

        types = ClassUtils.getHandlerTypes(AsyncMessageHandlerImpl.class);
        Assert.assertEquals(1, types.size());
        Assert.assertTrue(types.containsKey(ByteBuffer.class));
        Assert.assertTrue(types.get(ByteBuffer.class));

        types = ClassUtils.getHandlerTypes(ComplexMessageHandlerImpl.class);
        Assert.assertEquals(2, types.size());
        Assert.assertTrue(types.containsKey(ByteBuffer.class));
        Assert.assertFalse(types.get(ByteBuffer.class));
        Assert.assertTrue(types.containsKey(String.class));
        Assert.assertTrue(types.get(String.class));
        Assert.assertFalse(types.containsKey(byte[].class));

    }

    @Test
    public void testExtractEncoderType() {
        Class<?> clazz = ClassUtils.getEncoderType(BinaryEncoder.class);
        Assert.assertEquals(String.class, clazz);

        Class<?> clazz2 = ClassUtils.getEncoderType(TextEncoder.class);
        Assert.assertEquals(String.class, clazz2);

        Class<?> clazz3 = ClassUtils.getEncoderType(TextStreamEncoder.class);
        Assert.assertEquals(String.class, clazz3);

        Class<?> clazz4 = ClassUtils.getEncoderType(BinaryStreamEncoder.class);
        Assert.assertEquals(String.class, clazz4);
    }

    private static class MessageHandlerImpl implements MessageHandler.Whole<ByteBuffer> {
        @Override
        public void onMessage(ByteBuffer message) {
            // NOP
        }
    }

    private static final class AsyncMessageHandlerImpl implements MessageHandler.Partial<ByteBuffer> {

        @Override
        public void onMessage(final ByteBuffer partialMessage, final boolean last) {

        }
    }

    private static class DummyHandlerImpl extends MessageHandlerImpl {
        // NOP
    }

    private static final class ComplexMessageHandlerImpl extends DummyHandlerImpl implements MessageHandler.Partial<String> {

        @Override
        public void onMessage(String partialMessage, boolean last) {
            // NOP
        }

        public void onMessage(byte[] bytes, boolean last) {
            // NOP
        }

    }
    private static class ParamSuperclassEncoder<T> implements MessageHandler.Partial<T> {

        @Override
        public void onMessage(final T partialMessage, final boolean last) {

        }
    }


    private static final class ByteBufferFromSuperClassEncoder extends ParamSuperclassEncoder<ByteBuffer> {

    }

    private static final class BinaryEncoder implements Encoder.Binary<String> {
        @Override
        public ByteBuffer encode(String object) throws EncodeException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void init(final EndpointConfig config) {

        }

        @Override
        public void destroy() {

        }
    }

    private static class Im1<R, T, X, YY> extends ParamSuperclassEncoder<X> {
    }


    private static class Im2<X, Z, Y, Foo, Bar extends Test> extends Im1<List<String>, Z, Y, Integer> {
    }

    private static final class FinalIm extends Im2<String, Integer, ByteBuffer, String, Test> {
    }



    private static final class TextEncoder implements Encoder.Text<String> {
        @Override
        public String encode(String object) throws EncodeException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void init(final EndpointConfig config) {

        }

        @Override
        public void destroy() {

        }
    }

    private static final class TextStreamEncoder implements Encoder.TextStream<String> {
        @Override
        public void encode(String object, Writer writer) throws EncodeException, IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void init(final EndpointConfig config) {

        }

        @Override
        public void destroy() {

        }
    }


    private static final class BinaryStreamEncoder implements Encoder.BinaryStream<String> {
        @Override
        public void encode(String object, OutputStream stream) throws EncodeException, IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void init(final EndpointConfig config) {

        }

        @Override
        public void destroy() {

        }
    }
}
