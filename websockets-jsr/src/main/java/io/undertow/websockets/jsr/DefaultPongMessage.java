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

import org.xnio.Buffers;

import jakarta.websocket.PongMessage;
import java.nio.ByteBuffer;

/**
 * Default {@link PongMessage} implementation
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class DefaultPongMessage implements PongMessage {
    private static final PongMessage EMPTY = new DefaultPongMessage(Buffers.EMPTY_BYTE_BUFFER);
    private final ByteBuffer data;

    private DefaultPongMessage(ByteBuffer data) {
        this.data = data;
    }

    @Override
    public ByteBuffer getApplicationData() {
        return data;
    }

    /**
     * Create a {@link PongMessage} from the given {@link ByteBuffer}.
     */
    public static PongMessage create(ByteBuffer data) {
        if (data == null || data.hasRemaining()) {
            return new DefaultPongMessage(data);
        }
        return EMPTY;
    }
}
