/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;

public class AsyncSenderImpl implements Sender {

    final HttpServerExchange exchange;

    public AsyncSenderImpl(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void send(ByteBuf buffer, IoCallback<?> callback) {
        //TODO: use our own promise impl
        exchange.writeAsync(buffer, callback == IoCallback.END_EXCHANGE, callback, null);
    }

    @Override
    public void send(String data, Charset charset, IoCallback<?> callback) {
        exchange.writeAsync(Unpooled.copiedBuffer(data, StandardCharsets.UTF_8), callback == IoCallback.END_EXCHANGE, callback, null);
    }


    @Override
    public void transferFrom(FileChannel channel, IoCallback callback) {
        callback.onException(exchange, null, new IOException("NYI"));
    }

    @Override
    public void transferFrom(FileChannel channel, long start, long length, IoCallback callback) {
        callback.onException(exchange, null, new IOException("NYI"));
    }

    @Override
    public void close(IoCallback<?> callback) {
        exchange.writeAsync((ByteBuf) null, true, callback, null);
    }

}
