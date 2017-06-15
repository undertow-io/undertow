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
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.xnio.conduits.StreamSourceConduit;
import io.undertow.UndertowMessages;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import io.undertow.util.ObjectPool;

/**
 * @author Stuart Douglas
 */
public class GzipStreamSourceConduit extends InflatingStreamSourceConduit {

    public static final ConduitWrapper<StreamSourceConduit> WRAPPER = new ConduitWrapper<StreamSourceConduit>() {
        @Override
        public StreamSourceConduit wrap(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exchange) {
            return new GzipStreamSourceConduit(exchange, factory.create());
        }
    };

    private static final int GZIP_MAGIC = 0x8b1f;
    private static final byte[] HEADER = new byte[]{
            (byte) GZIP_MAGIC,        // Magic number (short)
            (byte) (GZIP_MAGIC >> 8),  // Magic number (short)
            Deflater.DEFLATED,        // Compression method (CM)
            0,                        // Flags (FLG)
            0,                        // Modification time MTIME (int)
            0,                        // Modification time MTIME (int)
            0,                        // Modification time MTIME (int)
            0,                        // Modification time MTIME (int)
            0,                        // Extra flags (XFLG)
            0                         // Operating system (OS)
    };
    private final CRC32 crc = new CRC32();

    public GzipStreamSourceConduit(HttpServerExchange exchange, StreamSourceConduit next) {
        super(exchange, next);
    }

    public GzipStreamSourceConduit(
            HttpServerExchange exchange,
            StreamSourceConduit next,
            ObjectPool<Inflater> inflaterPool) {
        super(exchange, next, inflaterPool);
    }

    private int totalOut;
    private int headerRead = 0;
    private int footerRead = 0;
    byte[] expectedFooter;

    protected boolean readHeader(ByteBuffer headerData) throws IOException {
        while (headerRead < HEADER.length && headerData.hasRemaining()) {
            byte data = headerData.get();
            if (headerRead == 0 && data != HEADER[0]) {
                throw UndertowMessages.MESSAGES.invalidGzipHeader();
            } else if (headerRead == 1 && data != HEADER[1]) {
                throw UndertowMessages.MESSAGES.invalidGzipHeader();
            }
            headerRead++;
        }
        return headerRead == HEADER.length;
    }

    protected void readFooter(ByteBuffer buf) throws IOException {
        if (expectedFooter == null) {
            byte[] ret = new byte[8];
            int checksum = (int) crc.getValue();
            int total = totalOut;
            ret[0] = (byte) ((checksum) & 0xFF);
            ret[1] = (byte) ((checksum >> 8) & 0xFF);
            ret[2] = (byte) ((checksum >> 16) & 0xFF);
            ret[3] = (byte) ((checksum >> 24) & 0xFF);
            ret[4] = (byte) ((total) & 0xFF);
            ret[5] = (byte) ((total >> 8) & 0xFF);
            ret[6] = (byte) ((total >> 16) & 0xFF);
            ret[7] = (byte) ((total >> 24) & 0xFF);
            expectedFooter = ret;
        }
        while (buf.hasRemaining() && footerRead < expectedFooter.length) {
            byte data = buf.get();
            if (expectedFooter[footerRead++] != data) {
                throw UndertowMessages.MESSAGES.invalidGZIPFooter();
            }

        }
        if (buf.hasRemaining() && footerRead == expectedFooter.length) {
            throw UndertowMessages.MESSAGES.invalidGZIPFooter();
        }
    }

    protected void dataDeflated(byte[] data, int off, int len) {
        crc.update(data, off, len);
        totalOut += len;
    }

}
