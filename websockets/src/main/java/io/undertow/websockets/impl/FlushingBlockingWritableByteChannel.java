/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.impl;

import org.xnio.channels.BlockingWritableByteChannel;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;

/**
 * {@link BlockingWritableByteChannel} implementation which take care of call {@link StreamSinkChannel#shutdownWrites()}
 * and {@link #flush()} on {@link #close()}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class FlushingBlockingWritableByteChannel extends BlockingWritableByteChannel {
    private final StreamSinkChannel delegate;

    FlushingBlockingWritableByteChannel(StreamSinkChannel delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public void close() throws IOException {
        delegate.shutdownWrites();
        flush();

        super.close();
    }
}
