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

import org.junit.Assert;
import org.junit.Test;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.MessageHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class ClassUtilsTest {

    @Test
    public void testExtractHandlerType() {
        Class<?> clazz = ClassUtils.getHandlerType(MessageHandlerImpl.class);
        Assert.assertEquals(ByteBuffer.class, clazz);

        Class<?> clazz2 = ClassUtils.getHandlerType(MessageHandlerImpl.class);
        Assert.assertEquals(ByteBuffer.class, clazz2);
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

    private static final class MessageHandlerImpl implements MessageHandler.Basic<ByteBuffer> {
        @Override
        public void onMessage(ByteBuffer message) {
            // NOOP
        }
    }

    private static final class AsyncMessageHandlerImpl implements MessageHandler.Async<ByteBuffer> {
        @Override
        public void onMessage(ByteBuffer message, boolean last) {
            // NOOP
        }
    }

    private static final class BinaryEncoder implements Encoder.Binary<String> {
        @Override
        public ByteBuffer encode(String object) throws EncodeException {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TextEncoder implements Encoder.Text<String> {
        @Override
        public String encode(String object) throws EncodeException {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TextStreamEncoder implements Encoder.TextStream<String> {
        @Override
        public void encode(String object, Writer writer) throws EncodeException, IOException {
            throw new UnsupportedOperationException();
        }
    }


    private static final class BinaryStreamEncoder implements Encoder.BinaryStream<String> {
        @Override
        public void encode(String object, OutputStream stream) throws EncodeException, IOException {
            throw new UnsupportedOperationException();
        }
    }
}
