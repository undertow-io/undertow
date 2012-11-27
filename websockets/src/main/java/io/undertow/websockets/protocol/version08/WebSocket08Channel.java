/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.protocol.version08;

import java.nio.ByteBuffer;

import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketVersion;
import io.undertow.websockets.protocol.version07.WebSocket07Channel;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;


/**
 * {@link WebSocketChannel} which is used for {@link WebSocketVersion#V08}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket08Channel extends WebSocket07Channel {
    public WebSocket08Channel(ConnectedStreamChannel channel, Pool<ByteBuffer> bufferPool, String wsUrl, boolean allowExtensions) {
        super(channel, bufferPool, wsUrl, allowExtensions);
    }

    @Override
    public WebSocketVersion getVersion() {
        return WebSocketVersion.V08;
    }
}

