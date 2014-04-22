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

package io.undertow.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * @author Stuart Douglas
 */
public class BrokenStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private final IOException exception;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param exception
     */
    public BrokenStreamSourceConduit(final StreamSourceConduit next, final IOException exception) {
        super(next);
        this.exception = exception;
    }

    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        throw exception;
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        throw exception;
    }


    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        throw exception;
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        throw exception;
    }
}
