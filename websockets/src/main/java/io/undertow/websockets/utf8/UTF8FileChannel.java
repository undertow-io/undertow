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

import io.undertow.websockets.wrapper.AbstractFileChannelWrapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class UTF8FileChannel extends AbstractFileChannelWrapper {
    private final UTF8Checker checker;

    public UTF8FileChannel(FileChannel fc, UTF8Checker checker) {
        super(fc);
        this.checker = checker;
    }

    @Override
    protected void beforeWriting(ByteBuffer buffer) throws IOException {
        checker.checkUTF8BeforeWrite(buffer);
    }

    @Override
    protected void afterReading(ByteBuffer buffer) throws IOException {
        checker.checkUTF8AfterRead(buffer);
    }

    @Override
    protected ReadableByteChannel wrapReadableByteChannel(ReadableByteChannel channel) {
        return new UTF8ReadableByteChannel(channel, checker);
    }

    @Override
    protected WritableByteChannel wrapWritableByteChannel(WritableByteChannel channel) {
        return new UTF8WritableByteChannel(channel, checker);
    }

    @Override
    protected AbstractFileChannelWrapper wrapFileChannel(FileChannel channel) {
        return new UTF8FileChannel(channel, checker);
    }
}
