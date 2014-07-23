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

package io.undertow.protocols.spdy;

import org.xnio.Pool;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Parser that supports push back when not all data can be read.
 *
 * @author Stuart Douglas
 */
public abstract class PushBackParser {

    private final Pool<ByteBuffer> bufferPool;
    private byte[] pushedBackData;
    private boolean finished;
    protected int streamId = -1;
    private int remainingData;

    public PushBackParser(Pool<ByteBuffer> bufferPool, int frameLength) {
        this.bufferPool = bufferPool;
        this.remainingData = frameLength;
    }

    public void parse(ByteBuffer data) throws IOException {
        int used = 0;
        ByteBuffer dataToParse = data;
        int oldLimit = dataToParse.limit();
        try {
            if (pushedBackData != null) {
                dataToParse = ByteBuffer.wrap(new byte[pushedBackData.length + data.remaining()]);
                dataToParse.put(pushedBackData);
                dataToParse.put(data);
                dataToParse.flip();
                oldLimit = dataToParse.limit();
            }
            if(dataToParse.remaining() > remainingData) {
                dataToParse.limit(dataToParse.position() + remainingData);
            }
            int rem = dataToParse.remaining();
            handleData(dataToParse);
            used = rem - dataToParse.remaining();

        } finally {
            int leftOver = dataToParse.remaining();
            if(leftOver > 0) {
                pushedBackData = new byte[leftOver];
                dataToParse.get(pushedBackData);
            } else {
                pushedBackData = null;
            }
            dataToParse.limit(oldLimit);
            remainingData -= used;
            if(remainingData == 0) {
                finished = true;
                finished();
            }
        }
    }

    protected void finished() throws IOException {

    }

    protected abstract void handleData(ByteBuffer resource) throws IOException;

    public boolean isFinished() {
        return finished;
    }

    public int getStreamId() {
        return streamId;
    }
}
