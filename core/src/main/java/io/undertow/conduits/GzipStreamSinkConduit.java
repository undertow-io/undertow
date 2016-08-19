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

import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSinkConduit;

import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * @author Stuart Douglas
 */
public class GzipStreamSinkConduit extends DeflatingStreamSinkConduit {

    /*
     * GZIP header magic number.
     */
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

    /**
     * CRC-32 of uncompressed data.
     */
    protected CRC32 crc = new CRC32();

    public GzipStreamSinkConduit(ConduitFactory<StreamSinkConduit> conduitFactory, HttpServerExchange exchange) {
        super(conduitFactory, exchange, Deflater.DEFAULT_COMPRESSION);
        writeHeader();
        Connectors.updateResponseBytesSent(exchange, HEADER.length);
    }

    private void writeHeader() {
        currentBuffer.getBuffer().put(HEADER);
    }

    @Override
    protected void preDeflate(byte[] data) {
        crc.update(data);
    }

    @Override
    protected byte[] getTrailer() {
        byte[] ret = new byte[8];
        int checksum = (int) crc.getValue();
        int total = deflater.getTotalIn();
        ret[0] = (byte) ((checksum) & 0xFF);
        ret[1] = (byte) ((checksum >> 8) & 0xFF);
        ret[2] = (byte) ((checksum >> 16) & 0xFF);
        ret[3] = (byte) ((checksum >> 24) & 0xFF);
        ret[4] = (byte) ((total) & 0xFF);
        ret[5] = (byte) ((total >> 8) & 0xFF);
        ret[6] = (byte) ((total >> 16) & 0xFF);
        ret[7] = (byte) ((total >> 24) & 0xFF);
        return ret;
    }
}
