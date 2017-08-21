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

package io.undertow.util;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * A simple write listener that can be used to write out the contents of a String. When the string is written
 * out it closes the channel.
 *
 * This should not be added directly to the channel, instead {@link #setup(org.xnio.channels.StreamSinkChannel)}
 * should be called, which will attempt a write, and only add the listener if required.
 *
 * @author Stuart Douglas
 */
public class StringWriteChannelListener extends ByteBufferWriteChannelListener {

    public StringWriteChannelListener( final String string) {
        this(string, Charset.defaultCharset());
    }

    public StringWriteChannelListener( final String string, Charset charset) {
        super(ByteBuffer.wrap(string.getBytes(charset)));
    }
}
