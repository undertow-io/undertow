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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.xnio.Bits;
import io.undertow.UndertowLogger;

import io.undertow.UndertowMessages;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

/**
 * Parser for HTTP2 headers
 *
 * @author Stuart Douglas
 */
abstract class Http2HeaderBlockParser extends Http2PushBackParser implements HpackDecoder.HeaderEmitter {

    private final HeaderMap headerMap = new HeaderMap();
    private boolean beforeHeadersHandled = false;

    private final HpackDecoder decoder;
    private int frameRemaining = -1;
    private boolean invalid = false;
    private boolean processingPseudoHeaders = true;
    private final boolean client;

    private int currentPadding;
    private final int streamId;

    //headers the server is allowed to receive
    private static final Set<HttpString> SERVER_HEADERS;

    static {
        Set<HttpString> server = new HashSet<>();
        server.add(Http2Channel.METHOD);
        server.add(Http2Channel.AUTHORITY);
        server.add(Http2Channel.SCHEME);
        server.add(Http2Channel.PATH);
        SERVER_HEADERS = Collections.unmodifiableSet(server);
    }

    Http2HeaderBlockParser(int frameLength, HpackDecoder decoder, boolean client, int streamId) {
        super(frameLength);
        this.decoder = decoder;
        this.client = client;
        this.streamId = streamId;
    }

    @Override
    protected void handleData(ByteBuffer resource, Http2FrameHeaderParser header) throws IOException {
        boolean continuationFramesComing = Bits.anyAreClear(header.flags, Http2Channel.HEADERS_FLAG_END_HEADERS);
        if (frameRemaining == -1) {
            frameRemaining = header.length;
        }
        final boolean moreDataThisFrame = resource.remaining() < frameRemaining;
        final int pos = resource.position();
        try {
            if (!beforeHeadersHandled) {
                int start = resource.position();
                if (!handleBeforeHeader(resource, header)) {
                    return;
                }
                currentPadding = getPaddingLength();
                frameRemaining -= (resource.position() - start);
            }
            beforeHeadersHandled = true;
            decoder.setHeaderEmitter(this);
            int oldLimit = -1;
            if(currentPadding > 0) {
                int actualData = frameRemaining - currentPadding;
                if(actualData < 0) {
                    throw new ConnectionErrorException(Http2Channel.ERROR_PROTOCOL_ERROR);
                }
                if(resource.remaining() > actualData) {
                    oldLimit = resource.limit();
                    resource.limit(resource.position() + actualData);
                }
            }
            try {
                decoder.decode(resource, moreDataThisFrame || continuationFramesComing);
            } catch (HpackException e) {
                throw new ConnectionErrorException(Http2Channel.ERROR_COMPRESSION_ERROR, e);
            }
            if(oldLimit != -1) {
                if(resource.remaining() == 0) {
                    int paddingInBuffer = oldLimit - resource.limit();
                    currentPadding -= paddingInBuffer;
                    resource.limit(oldLimit);
                    resource.position(oldLimit);
                } else {
                    resource.limit(oldLimit);
                }
            }
        } finally {
            int used = resource.position() - pos;
            frameRemaining -= used;
        }
    }

    protected abstract boolean handleBeforeHeader(ByteBuffer resource, Http2FrameHeaderParser header);


    HeaderMap getHeaderMap() {
        return headerMap;
    }

    @Override
    public void emitHeader(HttpString name, String value, boolean neverIndex) throws HpackException {
        headerMap.add(name, value);
        if(name.length() == 0) {
            throw UndertowMessages.MESSAGES.invalidHeader();
        }
        if(name.byteAt(0) == ':') {
            if(client) {
                if(!name.equals(Http2Channel.STATUS)) {
                    invalid = true;
                }
            } else {
                if(!SERVER_HEADERS.contains(name)) {
                    invalid = true;
                }
            }
            if(!processingPseudoHeaders) {
                throw UndertowMessages.MESSAGES.pseudoHeaderInWrongOrder(name);
            }
        } else {
            processingPseudoHeaders = false;
        }
        for(int i = 0; i < name.length(); ++i) {
            byte c = name.byteAt(i);
            if(c>= 'A' && c <= 'Z') {
                invalid = true;
                UndertowLogger.REQUEST_LOGGER.debugf("Malformed request, header %s contains uppercase characters", name);
            }
        }

    }
    protected abstract int getPaddingLength();
    @Override
    protected void moreData(int data) {
        super.moreData(data);
        frameRemaining += data;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public int getStreamId() {
        return streamId;
    }
}
