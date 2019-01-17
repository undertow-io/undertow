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

package io.undertow.websockets.jsr;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.websocket.RemoteEndpoint;

public class BinaryOutputStream extends OutputStream {

    final RemoteEndpoint.Basic basic;
    boolean closed;

    public BinaryOutputStream(RemoteEndpoint.Basic basic) {
        this.basic = basic;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b});
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("closed");
        }
        basic.sendBinary(ByteBuffer.wrap(b, off, len), false);
    }

    @Override
    public void close() throws IOException {
        basic.sendBinary(ByteBuffer.allocate(0), true);
    }
}
