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

package io.undertow.connector;

import java.io.Closeable;

/**
 * A pool of byte buffers
 *
 * @author Stuart Douglas
 */
public interface ByteBufferPool extends Closeable {

    PooledByteBuffer allocate();

    /**
     * If this byte buffer pool corresponds to an array backed pool then this will return itself.
     *
     * Otherwise it will return an array backed pool that contains buffers of the same size.
     *
     * @return An array backed pool of the same size
     */
    ByteBufferPool getArrayBackedPool();

    void close();

    int getBufferSize();

    boolean isDirect();
}
