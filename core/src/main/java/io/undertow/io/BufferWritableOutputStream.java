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

package io.undertow.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Represents an output stream that can write byte buffers
 * directly.
 *
 * @author Stuart Douglas
 */
public interface BufferWritableOutputStream {

    void write(ByteBuffer[] buffers) throws IOException;

    void write(ByteBuffer byteBuffer) throws IOException;

    /**
     * Transfer the remaining content of the input FileChannel (from its current position, to end of file).
     * @deprecated use {@link BufferWritableOutputStream#transferFrom(FileChannel, long, long)}.
     */
    @Deprecated
    void transferFrom(FileChannel source) throws IOException;

    /**
     * Transfer from the input channel to the channel underlying this OutputStream.
     * @param source the source file channel
     * @param startPosition the start position in the source file
     * @param count the number of bytes to transfer
     */
    void transferFrom(FileChannel source, long startPosition, long count) throws IOException;

}
