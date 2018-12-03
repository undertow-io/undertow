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

package io.undertow.servlet.spec;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.UndertowOutputStream;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.UndertowOptionMap;

/**
 * This stream essentially has two modes. When it is being used in standard blocking mode then
 * it will buffer in the pooled buffer. If the stream is closed before the buffer is full it will
 * set a content-length header if one has not been explicitly set.
 * <p>
 * If a content-length header was present when the stream was created then it will automatically
 * close and flush itself once the appropriate amount of data has been written.
 * <p>
 * Once the listener has been set it goes into async mode, and writes become non blocking. Most methods
 * have two different code paths, based on if the listener has been set or not
 * <p>
 * Once the write listener has been set operations must only be invoked on this stream from the write
 * listener callback. Attempting to invoke from a different thread will result in an IllegalStateException.
 * <p>
 * Async listener tasks are queued in the {@link AsyncContextImpl}. At most one lister can be active at
 * one time, which simplifies the thread safety requirements.
 *
 * @author Stuart Douglas
 */
public class ServletOutputStreamImpl extends ServletOutputStream {

    private final HttpServerExchange exchange;
    private final UndertowOutputStream delegate;

    private int bufferSize;

    public ServletOutputStreamImpl(HttpServerExchange exchange) {
        this.exchange = exchange;
        this.delegate = new UndertowOutputStream(exchange);
    }

    public ServletOutputStreamImpl(HttpServerExchange exchange, Integer bufferSize) {
        this.exchange = exchange;
        this.delegate = new UndertowOutputStream(exchange);
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        throw new RuntimeException("NYI");
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    public void resetBuffer() {

    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public ServletRequestContext getServletRequestContext() {
        return null;
    }

    public ByteBuffer underlyingBuffer() {
        return null;
    }

    public void flushInternal() throws IOException {

    }

    public void updateWritten(int i) {

    }
}
