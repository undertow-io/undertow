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

import io.undertow.UndertowMessages;

/**
 * Parser that supports push back when not all data can be read.
 *
 * @author Stuart Douglas
 */
public abstract class Http2PushBackParser {

    private byte[] pushedBackData;
    private boolean finished;
    private int remainingData;
    private final int frameLength;

    int cnt;

    public Http2PushBackParser(int frameLength) {
        this.remainingData = frameLength;
        this.frameLength = frameLength;
    }

    public void parse(ByteBuffer data, Http2FrameHeaderParser headerParser) throws IOException {
        int used = 0;
        ByteBuffer dataToParse = data;
        int oldLimit = data.limit();
        try {
            if (pushedBackData != null) {
                int toCopy = Math.min(remainingData - pushedBackData.length, data.remaining());
                dataToParse = ByteBuffer.wrap(new byte[pushedBackData.length + toCopy]);
                dataToParse.put(pushedBackData);
                data.limit(data.position() + toCopy);
                dataToParse.put(data);
                dataToParse.flip();
            }
            if (dataToParse.remaining() > remainingData) {
                dataToParse.limit(dataToParse.position() + remainingData);
            }
            int rem = dataToParse.remaining();
            handleData(dataToParse, headerParser);
            used = rem - dataToParse.remaining();
            if(!isFinished() && remainingData > 0 && used == 0 && dataToParse.remaining() >= remainingData) {
                if(cnt++ == 100) {
                    throw UndertowMessages.MESSAGES.parserDidNotMakeProgress();
                }
            }

        } finally {
            //it is possible that we finished the parsing without using up all the data
            //and the rest is to be consumed by the stream itself
            if (finished) {
                data.limit(oldLimit);
                return;
            }
            int leftOver = dataToParse.remaining();
            if (leftOver > 0) {
                pushedBackData = new byte[leftOver];
                dataToParse.get(pushedBackData);
            } else {
                pushedBackData = null;
            }
            data.limit(oldLimit);
            remainingData -= used;
            if (remainingData == 0) {
                finished = true;
            }
        }
    }

    protected abstract void handleData(ByteBuffer resource, Http2FrameHeaderParser headerParser) throws IOException;

    public boolean isFinished() {
        if(pushedBackData != null && remainingData == pushedBackData.length) {
            return true;
        }
        return finished;
    }

    protected void finish() {
        finished = true;
    }

    protected void moreData(int data) {
        finished = false;
        this.remainingData += data;
    }

    public int getFrameLength() {
        return frameLength;
    }
}
