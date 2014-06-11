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

package io.undertow.websockets.spi;

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.core.WebSocketChannel;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class BlockingWebSocketHttpServerExchange extends AsyncWebSocketHttpServerExchange {

    private final OutputStream out;
    private final InputStream in;

    public BlockingWebSocketHttpServerExchange(final HttpServerExchange exchange, Set<WebSocketChannel> peerConnections) {
        super(exchange, peerConnections);
        out = exchange.getOutputStream();
        in = exchange.getInputStream();
    }

    @Override
    public IoFuture<Void> sendData(final ByteBuffer data) {
        try {
            while (data.hasRemaining()) {
                out.write(data.get());
            }
            return new FinishedIoFuture<>(null);
        } catch (IOException e) {
            final FutureResult<Void> ioFuture = new FutureResult<>();
            ioFuture.setException(e);
            return ioFuture.getIoFuture();
        }
    }

    @Override
    public IoFuture<byte[]> readRequestData() {
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                data.write(buf, 0, r);
            }
            return new FinishedIoFuture<>(data.toByteArray());
        } catch (IOException e) {
            final FutureResult<byte[]> ioFuture = new FutureResult<>();
            ioFuture.setException(e);
            return ioFuture.getIoFuture();
        }
    }
}
