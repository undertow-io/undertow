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

package io.undertow.xnio.io;

import java.nio.channels.FileChannel;

import org.xnio.channels.StreamSinkChannel;

import io.undertow.connector.IoExecutor;
import io.undertow.connector.IoResult;
import io.undertow.connector.IoSink;
import io.undertow.connector.PooledByteBuffer;

public class XnioIoSink implements IoSink {

    protected PooledByteBuffer buffer;

    public XnioIoSink(StreamSinkChannel responseChannel) {

    }

    @Override
    public IoResult<Void> write(PooledByteBuffer data) {
        return null;
    }

    @Override
    public IoResult<Void> write(PooledByteBuffer[] data) {
        return null;
    }

    @Override
    public IoResult<Void> writeAndClose(PooledByteBuffer data) {
        return null;
    }

    @Override
    public IoResult<Void> writeAndClose(PooledByteBuffer[] data) {
        return null;
    }

    @Override
    public IoResult<Void> write(byte[] data) {
        return null;
    }

    @Override
    public IoResult<Void> write(byte[] data, int offset, int len) {
        return null;
    }

    @Override
    public IoResult<Void> writeAndClose(byte[] data) {
        return null;
    }

    @Override
    public IoResult<Void> writeAndClose(byte[] data, int offset, int len) {
        return null;
    }

    @Override
    public IoResult<Void> flush() {
        return null;
    }

    @Override
    public IoResult<Void> close() {
        return null;
    }

    @Override
    public void terminate() {

    }

    @Override
    public IoExecutor getExecutor() {
        return null;
    }

    @Override
    public void sendFile(FileChannel fileChannel, boolean closeFile) {

    }
}
