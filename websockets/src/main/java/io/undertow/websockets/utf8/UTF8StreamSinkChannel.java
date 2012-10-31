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
package io.undertow.websockets.utf8;

import io.undertow.websockets.wrapper.AbstractStreamSinkChannelWrapper;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class UTF8StreamSinkChannel extends AbstractStreamSinkChannelWrapper {

    private final UTF8Checker checker;

    public UTF8StreamSinkChannel(StreamSinkChannel channel, UTF8Checker checker) {
        super(channel);
        this.checker = checker;
    }

    @Override
    protected void beforeWriting(ByteBuffer buffer) throws IOException {
        checker.checkUTF8BeforeWrite(buffer);
    }

    @Override
    protected StreamSourceChannel wrapStreamSourceChannel(StreamSourceChannel channel) {
        return new UTF8StreamSourceChannel(channel, checker);
    }

    @Override
    protected FileChannel wrapFileChannel(FileChannel channel) {
        return new UTF8FileChannel(channel, checker);
    }
}
