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

import io.undertow.server.protocol.http.HttpContinue;
import org.xnio.Bits;
import io.undertow.UndertowLogger;

import io.undertow.UndertowMessages;
import io.undertow.server.Connectors;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
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
    private int totalHeaderLength = 0;
    private boolean invalid = false;
    private boolean processingPseudoHeaders = true;
    private final boolean client;
    private final int maxHeaders;
    private final int maxHeaderListSize;

    private int currentPadding;
    private final int streamId;
    private int headerSize;

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

    Http2HeaderBlockParser(int frameLength, HpackDecoder decoder, boolean client, int maxHeaders, int streamId, int maxHeaderListSize) {
        super(frameLength);
        this.decoder = decoder;
        this.client = client;
        this.maxHeaders = maxHeaders;
        this.streamId = streamId;
        this.maxHeaderListSize = maxHeaderListSize;
    }

    @Override
    protected void handleData(ByteBuffer resource, Http2FrameHeaderParser header) throws IOException {
        boolean continuationFramesComing = Bits.anyAreClear(header.flags, Http2Channel.HEADERS_FLAG_END_HEADERS);
        if (frameRemaining == -1) {
            frameRemaining = totalHeaderLength = header.length;
        }
        final boolean moreDataThisFrame = resource.remaining() < frameRemaining;
        final int pos = resource.position();
        int readInBeforeHeader = 0;
        try {
            if (!beforeHeadersHandled) {
                if (!handleBeforeHeader(resource, header)) {
                    return;
                }
                currentPadding = getPaddingLength();
                readInBeforeHeader = resource.position() - pos;
            }
            beforeHeadersHandled = true;
            decoder.setHeaderEmitter(this);
            int oldLimit = -1;
            if(currentPadding > 0) {
                int actualData = frameRemaining - readInBeforeHeader - currentPadding;
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
                throw new ConnectionErrorException(e.getCloseCode(), e);
            }

            if(maxHeaders > 0 && headerMap.size() > maxHeaders) {
                throw new StreamErrorException(Http2Channel.ERROR_FRAME_SIZE_ERROR);
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

    boolean isContentExpected() {
        if (HttpContinue.requiresContinueResponse(headerMap)) {
            return true;
        }
        String contentLengthString = headerMap.getFirst(Headers.CONTENT_LENGTH);
        try {
            return contentLengthString != null ? Long.parseLong(contentLengthString) > 0 : false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void emitHeader(HttpString name, String value, boolean neverIndex) throws HpackException {
        if(maxHeaderListSize > 0) {
            headerSize += (name.length() + value.length() + 32);
            if (headerSize > maxHeaderListSize) {
                throw new HpackException(UndertowMessages.MESSAGES.headerBlockTooLarge(maxHeaderListSize), Http2Channel.ERROR_PROTOCOL_ERROR);
            }
        }
        if(maxHeaders > 0 && headerMap.size() > maxHeaders) {
            return;
        }
        headerMap.add(name, value);

        if(name.length() == 0) {
            throw UndertowMessages.MESSAGES.invalidHeader();
        }
        if(name.equals(Headers.TRANSFER_ENCODING)) {
            throw new HpackException(Http2Channel.ERROR_PROTOCOL_ERROR);
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
                throw new HpackException(UndertowMessages.MESSAGES.pseudoHeaderInWrongOrder(name), Http2Channel.ERROR_PROTOCOL_ERROR);
            }
        } else {
            processingPseudoHeaders = false;
        }
        for(int i = 0; i < name.length(); ++i) {
            byte c = name.byteAt(i);
            if(c>= 'A' && c <= 'Z') {
                invalid = true;
                UndertowLogger.REQUEST_LOGGER.debugf("Malformed request, header %s contains uppercase characters", name);
            } else if(c != ':' && !Connectors.isValidTokenCharacter(c)) {
                invalid = true;
                UndertowLogger.REQUEST_LOGGER.debugf("Malformed request, header %s contains invalid token character", name);
            }
        }

    }
    protected abstract int getPaddingLength();
    @Override
    protected boolean moreData(int data) {
        boolean acceptMoreData = super.moreData(data);
        frameRemaining += data;
        totalHeaderLength += data;
        if (maxHeaderListSize > 0 && totalHeaderLength > maxHeaderListSize) {
            UndertowLogger.REQUEST_LOGGER.debug(UndertowMessages.MESSAGES.headerBlockTooLarge(maxHeaderListSize));
            return false;
        }
        return acceptMoreData;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public int getStreamId() {
        return streamId;
    }
}
