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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * ReadableByteChannel which wraps another ReadableByteChannel and check if the read data contain
 * any non UTF-8 data. If that is the case it will throw an {@link java.io.UnsupportedEncodingException}
 *
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class UTF8ReadableByteChannel implements ReadableByteChannel {
    protected final ReadableByteChannel channel;
    protected final UTF8Checker checker;

    public UTF8ReadableByteChannel(ReadableByteChannel channel, UTF8Checker checker) {
        this.channel = channel;
        this.checker = checker;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int pos = dst.position();
        int r = channel.read(dst);

        checker.checkUTF8(dst, pos, r);
        return r;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
