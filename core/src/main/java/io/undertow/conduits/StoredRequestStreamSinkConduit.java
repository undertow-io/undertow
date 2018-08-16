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

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.StreamSourceConduit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Farid Zakaria
 */
public class StoredRequestStreamSinkConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    public static final AttachmentKey<byte[]> REQUEST = AttachmentKey.create(byte[].class);

    private final HttpServerExchange exchange;

    private ByteArrayOutputStream outputStream;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    public StoredRequestStreamSinkConduit(StreamSourceConduit next, HttpServerExchange exchange) {
        super(next);
        this.exchange = exchange;
        long length = exchange.getResponseContentLength();
        //Could be -1 if it is chunked transfer encoding
        if (length <= 0L) {
            outputStream = new ByteArrayOutputStream();
        } else {
            if (length > Integer.MAX_VALUE) {
                throw UndertowMessages.MESSAGES.responseTooLargeToBuffer(length);
            }
            outputStream = new ByteArrayOutputStream((int) length);
        }
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
    }


    @Override
    public int read(ByteBuffer dst) throws IOException {
        int pos = dst.position();
        int res = super.read(dst);
        if (res > 0) {
            byte[] d = new byte[res];
            for (int i = 0; i < res; ++i) {
                d[i] = dst.get(i + pos);
            }
            outputStream.write(d);
        }

        //Attach the request body once finished
        if (res == -1) {
            exchange.putAttachment(REQUEST, outputStream.toByteArray());
            outputStream = null;
        }

        return res;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
        for (int i = offs; i < len; ++i) {
            if (dsts[i].hasRemaining()) {
                return read(dsts[i]);
            }
        }
        return 0;
    }

}
