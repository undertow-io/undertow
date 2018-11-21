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

package io.undertow.connector;

import java.nio.channels.FileChannel; /**
 * An interface that represents a data sink
 */
public interface IoSink {

    IoResult<Void> write(PooledByteBuffer data);

    IoResult<Void> write(PooledByteBuffer[] data);

    IoResult<Void> writeAndClose(PooledByteBuffer data);

    IoResult<Void> writeAndClose(PooledByteBuffer[] data);

    IoResult<Void> write(byte[] data);

    IoResult<Void> write(byte[] data, int offset, int len);

    IoResult<Void> writeAndClose(byte[] data);

    IoResult<Void> writeAndClose(byte[] data, int offset, int len);

    IoResult<Void> flush();

    IoResult<Void> close();

    void terminate();

    IoExecutor getExecutor();

    void sendFile(FileChannel fileChannel, boolean closeFile);
}
