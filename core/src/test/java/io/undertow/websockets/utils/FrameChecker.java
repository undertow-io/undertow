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
package io.undertow.websockets.utils;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.junit.Assert;
import org.xnio.FutureResult;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class FrameChecker implements WebSocketTestClient.FrameListener {
    private final Class<? extends WebSocketFrame> clazz;
    private final byte[] expectedPayload;
    private final FutureResult<?> latch;

    public FrameChecker(Class<? extends WebSocketFrame> clazz, byte[] expectedPayload, FutureResult<?> latch) {
        this.clazz = clazz;
        this.expectedPayload = expectedPayload;
        this.latch = latch;
    }


    @Override
    public void onFrame(WebSocketFrame frame) {
        try {
            Assert.assertTrue(clazz.isInstance(frame));

            if (frame instanceof TextWebSocketFrame) {
                String buf = ((TextWebSocketFrame) frame).getText();

                Assert.assertEquals(new String(expectedPayload, Charset.forName("UTF-8")), buf);
            } else {
                ChannelBuffer buf = frame.getBinaryData();
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);

                Assert.assertArrayEquals(expectedPayload, data);
            }
            latch.setResult(null);
        } catch (Throwable e) {
            latch.setException(new IOException(e));
        }
    }

    @Override
    public void onError(Throwable t) {
        try {
            t.printStackTrace();
            Assert.fail();
        } finally {
            latch.setException(new IOException(t));
        }
    }
}
