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

package io.undertow.protocols.http2;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Parser that supports push back when not all data can be read.
 *
 * @author Stuart Douglas
 */
public abstract class Http2PushBackParser {

    private byte[] pushedBackData;
    private boolean finished;
    private int remainingData;

    public Http2PushBackParser(int frameLength) {
        this.remainingData = frameLength;
    }

    public void parse(ByteBuffer data, Http2FrameHeaderParser headerParser) throws IOException {
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
            if (dataToParse.remaining() > remainingData) {
                dataToParse.limit(dataToParse.position() + remainingData);
            }
            int rem = dataToParse.remaining();
            handleData(dataToParse, headerParser);
            used = rem - dataToParse.remaining();

        } finally {
            //it is possible that we finished the parsing without using up all the data
            //and the rest is to be consumed by the stream itself
            if (finished) {
                dataToParse.limit(oldLimit);
                return;
            }
            int leftOver = dataToParse.remaining();
            if (leftOver > 0) {
                pushedBackData = new byte[leftOver];
                dataToParse.get(pushedBackData);
            } else {
                pushedBackData = null;
            }
            dataToParse.limit(oldLimit);
            remainingData -= used;
            if (remainingData == 0) {
                finished = true;
            }
        }
    }

    protected abstract void handleData(ByteBuffer resource, Http2FrameHeaderParser headerParser) throws IOException;

    public boolean isFinished() {
        return finished;
    }

    protected void finish() {
        finished = true;
    }
}
