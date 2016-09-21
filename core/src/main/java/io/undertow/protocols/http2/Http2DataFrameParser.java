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

import java.nio.ByteBuffer;
import org.xnio.Bits;

/**
 * Parses the data frame. If the passing flag has not been set then there is nothing to parse.
 *
 * @author Stuart Douglas
 */
class Http2DataFrameParser extends Http2PushBackParser {

    private int padding = 0;

    Http2DataFrameParser(int frameLength) {
        super(frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource, Http2FrameHeaderParser headerParser) throws ConnectionErrorException {
        if (Bits.anyAreClear(headerParser.flags, Http2Channel.DATA_FLAG_PADDED)) {
            finish();
            return;
        }
        if(headerParser.length == 0) {
            //empty frame with padding set
            //which is wrong
            throw new ConnectionErrorException(Http2Channel.ERROR_PROTOCOL_ERROR);
        }
        if (resource.remaining() > 0) {
            padding = resource.get() & 0xFF;
            headerParser.length--; //decrement the length by one as we have consumed a byte
            if(padding > headerParser.length) {
                throw new ConnectionErrorException(Http2Channel.ERROR_PROTOCOL_ERROR);
            }
            finish();
        }
    }

    int getPadding() {
        return padding;
    }
}
