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
package io.undertow.websockets.core.function;

import io.undertow.server.protocol.framed.FrameHeaderData;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface ChannelFunction {


    void newFrame(FrameHeaderData headerData);

    /**
     * Is called on the {@link ByteBuffer} after a read operation completes
     *
     * @param buf           the {@link ByteBuffer} to operate on
     * @param position      the index in the {@link ByteBuffer} to start from
     * @param length        the number of bytes to operate on
     * @throws IOException  thrown if an error occurs
     */
    void afterRead(ByteBuffer buf, int position, int length) throws IOException;

    /**
     * Is called on the {@link ByteBuffer} before a write operation completes
     *
     * @param buf           the {@link ByteBuffer} to operate on
     * @param position      the index in the {@link ByteBuffer} to start from
     * @param length        the number of bytes to operate on
     * @throws IOException  thrown if an error occurs
     */
    void beforeWrite(ByteBuffer buf, int position, int length) throws IOException;

    /**
     * Is called to complete the {@link ChannelFunction}. Access it after complete
     * is called may result in unexpected behavior.
     *
     * @throws IOException  thrown if an error occurs
     */
    void complete() throws IOException;
}
