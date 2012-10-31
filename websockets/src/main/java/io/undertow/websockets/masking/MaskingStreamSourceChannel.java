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
package io.undertow.websockets.masking;

import io.undertow.websockets.wrapper.AbstractStreamSourceChannelWrapper;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class MaskingStreamSourceChannel extends AbstractStreamSourceChannelWrapper {
    private final Masker masker;

    public MaskingStreamSourceChannel(StreamSourceChannel channel, Masker masker) {
        super(channel);
        this.masker = masker;
    }

    @Override
    protected void afterReading(ByteBuffer buffer) throws IOException {
        masker.maskAfterRead(buffer);
    }

    @Override
    protected StreamSinkChannel wrapStreamSinkChannel(StreamSinkChannel channel) {
        return new MaskingStreamSinkChannel(channel, masker);
    }

    @Override
    protected FileChannel wrapFileChannel(FileChannel channel) {
        return new MaskingFileChannel(channel, masker);
    }
}
