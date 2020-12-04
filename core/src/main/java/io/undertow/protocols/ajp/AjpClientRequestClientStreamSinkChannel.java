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

package io.undertow.protocols.ajp;

import static io.undertow.protocols.ajp.AjpConstants.ATTR_AUTH_TYPE;
import static io.undertow.protocols.ajp.AjpConstants.ATTR_QUERY_STRING;
import static io.undertow.protocols.ajp.AjpConstants.ATTR_REMOTE_USER;
import static io.undertow.protocols.ajp.AjpConstants.ATTR_ROUTE;
import static io.undertow.protocols.ajp.AjpConstants.ATTR_SECRET;
import static io.undertow.protocols.ajp.AjpConstants.ATTR_SSL_CERT;
import static io.undertow.protocols.ajp.AjpConstants.ATTR_SSL_CIPHER;
import static io.undertow.protocols.ajp.AjpConstants.ATTR_SSL_KEY_SIZE;
import static io.undertow.protocols.ajp.AjpConstants.ATTR_SSL_SESSION;
import static io.undertow.protocols.ajp.AjpConstants.ATTR_STORED_METHOD;
import static io.undertow.protocols.ajp.AjpUtils.notNull;
import static io.undertow.protocols.ajp.AjpUtils.putString;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.xnio.ChannelListener;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.client.ProxiedRequestAttachments;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.Attachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HexConverter;
import io.undertow.util.HttpString;
import io.undertow.util.ImmediatePooledByteBuffer;

/**
 * AJP stream sink channel that corresponds to a request send from the load balancer to the backend
 *
 * @author Stuart Douglas
 */
public class AjpClientRequestClientStreamSinkChannel extends AbstractAjpClientStreamSinkChannel {

    private final ChannelListener<AjpClientRequestClientStreamSinkChannel> finishListener;

    public static final int DEFAULT_MAX_DATA_SIZE = 8192;

    private final HeaderMap headers;
    private final String path;
    private final HttpString method;
    private final HttpString protocol;
    private final Attachable attachable;


    private boolean firstFrameWritten = false;
    private long dataSize;
    private int requestedChunkSize = -1;
    private SendFrameHeader header;
    /**
     * When we are the client and the
     */
    private boolean discardMode = false;

    AjpClientRequestClientStreamSinkChannel(AjpClientChannel channel, ChannelListener<AjpClientRequestClientStreamSinkChannel> finishListener, HeaderMap headers, String path, HttpString method, HttpString protocol, Attachable attachable) {
        super(channel);
        this.finishListener = finishListener;
        this.headers = headers;
        this.path = path;
        this.method = method;
        this.protocol = protocol;
        this.attachable = attachable;
    }


    private SendFrameHeader createFrameHeaderImpl() {
        if (discardMode) {
            getBuffer().clear();
            getBuffer().flip();
            return new SendFrameHeader(new ImmediatePooledByteBuffer(ByteBuffer.wrap(new byte[0])));
        }
        PooledByteBuffer pooledHeaderBuffer = getChannel().getBufferPool().allocate();
        try {

            final ByteBuffer buffer = pooledHeaderBuffer.getBuffer();
            ByteBuffer dataBuffer = getBuffer();
            int dataInBuffer = dataBuffer.remaining();
            if (!firstFrameWritten && requestedChunkSize == 0) {
                //we are waiting on a read body chunk
                return new SendFrameHeader(dataInBuffer, null);
            }
            int maxData = getChannel().getSettings().get(UndertowOptions.MAX_AJP_PACKET_SIZE, DEFAULT_MAX_DATA_SIZE) - 6;

            if (!firstFrameWritten) {
                String contentLength = headers.getFirst(Headers.CONTENT_LENGTH);
                if (contentLength != null) {
                    dataSize = Long.parseLong(contentLength);
                    requestedChunkSize = maxData;
                    if (dataInBuffer > dataSize) {
                        throw UndertowMessages.MESSAGES.fixedLengthOverflow();
                    }
                } else if (isWritesShutdown() && !headers.contains(Headers.TRANSFER_ENCODING)) {
                    //writes are shut down, go to fixed length
                    headers.put(Headers.CONTENT_LENGTH, dataInBuffer);
                    dataSize = dataInBuffer;
                    requestedChunkSize = maxData;
                } else {
                    headers.put(Headers.TRANSFER_ENCODING, Headers.CHUNKED.toString());
                    dataSize = -1;
                    requestedChunkSize = 0;
                }

                firstFrameWritten = true;
                final String path;
                final String queryString;
                int qsIndex = this.path.indexOf('?');
                if (qsIndex == -1) {
                    path = this.path;
                    queryString = null;
                } else {
                    path = this.path.substring(0, qsIndex);
                    queryString = this.path.substring(qsIndex + 1);
                }

                buffer.put((byte) 0x12);
                buffer.put((byte) 0x34);
                buffer.put((byte) 0); //we fill the size in later
                buffer.put((byte) 0);
                buffer.put((byte) 2);
                boolean storeMethod = false;
                Integer methodNp = AjpConstants.HTTP_METHODS_MAP.get(method);
                if (methodNp == null) {
                    methodNp = 0xFF;
                    storeMethod = true;
                }
                buffer.put((byte) (int) methodNp);
                AjpUtils.putHttpString(buffer, protocol);
                putString(buffer, path);
                putString(buffer, notNull(attachable.getAttachment(ProxiedRequestAttachments.REMOTE_ADDRESS)));
                putString(buffer, notNull(attachable.getAttachment(ProxiedRequestAttachments.REMOTE_HOST)));
                putString(buffer, notNull(attachable.getAttachment(ProxiedRequestAttachments.SERVER_NAME)));
                AjpUtils.putInt(buffer, notNull(attachable.getAttachment(ProxiedRequestAttachments.SERVER_PORT)));
                buffer.put((byte) (notNull(attachable.getAttachment(ProxiedRequestAttachments.IS_SSL)) ? 1 : 0));

                int headers = 0;
                //we need to count the headers
                final HeaderMap responseHeaders = this.headers;
                for (HttpString name : responseHeaders.getHeaderNames()) {
                    headers += responseHeaders.get(name).size();
                }

                AjpUtils.putInt(buffer, headers);


                for (final HttpString header : responseHeaders.getHeaderNames()) {
                    for (String headerValue : responseHeaders.get(header)) {
                        Integer headerCode = AjpConstants.HEADER_MAP.get(header);
                        if (headerCode != null) {
                            AjpUtils.putInt(buffer, headerCode);
                        } else {
                            AjpUtils.putHttpString(buffer, header);
                        }
                        putString(buffer, headerValue);
                    }
                }

                if (queryString != null) {
                    buffer.put((byte) ATTR_QUERY_STRING); //query_string
                    putString(buffer, queryString);
                }
                String remoteUser = attachable.getAttachment(ProxiedRequestAttachments.REMOTE_USER);
                if (remoteUser != null) {
                    buffer.put((byte) ATTR_REMOTE_USER);
                    putString(buffer, remoteUser);
                }
                String authType = attachable.getAttachment(ProxiedRequestAttachments.AUTH_TYPE);
                if (authType != null) {
                    buffer.put((byte) ATTR_AUTH_TYPE);
                    putString(buffer, authType);
                }
                String route = attachable.getAttachment(ProxiedRequestAttachments.ROUTE);
                if (route != null) {
                    buffer.put((byte) ATTR_ROUTE);
                    putString(buffer, route);
                }
                String sslCert = attachable.getAttachment(ProxiedRequestAttachments.SSL_CERT);
                if (sslCert != null) {
                    buffer.put((byte) ATTR_SSL_CERT);
                    putString(buffer, sslCert);
                }
                String sslCypher = attachable.getAttachment(ProxiedRequestAttachments.SSL_CYPHER);
                if (sslCypher != null) {
                    buffer.put((byte) ATTR_SSL_CIPHER);
                    putString(buffer, sslCypher);
                }
                byte[] sslSession = attachable.getAttachment(ProxiedRequestAttachments.SSL_SESSION_ID);
                if (sslSession != null) {
                    buffer.put((byte) ATTR_SSL_SESSION);
                    putString(buffer, HexConverter.convertToHexString(sslSession));
                }
                Integer sslKeySize = attachable.getAttachment(ProxiedRequestAttachments.SSL_KEY_SIZE);
                if (sslKeySize != null) {
                    buffer.put((byte) ATTR_SSL_KEY_SIZE);
                    putString(buffer, sslKeySize.toString());
                }
                String secret = attachable.getAttachment(ProxiedRequestAttachments.SECRET);
                if (secret != null) {
                    buffer.put((byte) ATTR_SECRET);
                    putString(buffer, secret);
                }

                if (storeMethod) {
                    buffer.put((byte) ATTR_STORED_METHOD);
                    putString(buffer, method.toString());
                }
                buffer.put((byte) 0xFF);

                int dataLength = buffer.position() - 4;
                buffer.put(2, (byte) ((dataLength >> 8) & 0xFF));
                buffer.put(3, (byte) (dataLength & 0xFF));
            }
            if (dataSize == 0) {
                //no data, just write out this frame and we are done
                buffer.flip();
                return new SendFrameHeader(pooledHeaderBuffer);
            } else if (requestedChunkSize > 0) {

                if (isWritesShutdown() && dataInBuffer == 0) {
                    buffer.put((byte) 0x12);
                    buffer.put((byte) 0x34);
                    buffer.put((byte) 0x00);
                    buffer.put((byte) 0x02);
                    buffer.put((byte) 0x00);
                    buffer.put((byte) 0x00);
                    buffer.flip();
                    return new SendFrameHeader(pooledHeaderBuffer);
                }
                int remaining = dataInBuffer;
                remaining = Math.min(remaining, maxData);
                remaining = Math.min(remaining, requestedChunkSize);
                int bodySize = remaining + 2;
                buffer.put((byte) 0x12);
                buffer.put((byte) 0x34);
                buffer.put((byte) ((bodySize >> 8) & 0xFF));
                buffer.put((byte) (bodySize & 0xFF));
                buffer.put((byte) ((remaining >> 8) & 0xFF));
                buffer.put((byte) (remaining & 0xFF));
                requestedChunkSize = 0;
                if (remaining < dataInBuffer) {
                    dataBuffer.limit(getBuffer().position() + remaining);
                    buffer.flip();
                    return new SendFrameHeader(dataInBuffer - remaining, pooledHeaderBuffer, dataSize < 0);
                } else {
                    buffer.flip();
                    return new SendFrameHeader(0, pooledHeaderBuffer, dataSize < 0);
                }
            } else {
                //chunked. We just write the headers, and leave all the data in the buffer
                //they need to send us a read body chunk in order to get any data
                buffer.flip();
                if (buffer.remaining() == 0) {
                    pooledHeaderBuffer.close();
                    return new SendFrameHeader(dataInBuffer, null, true);
                }
                dataBuffer.limit(dataBuffer.position());
                return new SendFrameHeader(dataInBuffer, pooledHeaderBuffer, true);
            }
        } catch (BufferOverflowException e) {
            //TODO: UNDERTOW-901
            pooledHeaderBuffer.close();
            markBroken();
            throw e;
        }
    }

    SendFrameHeader generateSendFrameHeader() {
        header = createFrameHeaderImpl();
        return header;
    }

    void chunkRequested(int size) throws IOException {
        requestedChunkSize = size;
        getChannel().recalculateHeldFrames();
    }

    public void startDiscard() {
        discardMode = true;
        try {
            getChannel().recalculateHeldFrames();
        } catch (IOException e) {
            markBroken();
        }
    }

    @Override
    protected final SendFrameHeader createFrameHeader() {
        SendFrameHeader header = this.header;
        this.header = null;
        return header;
    }

    @Override
    protected void handleFlushComplete(boolean finalFrame) {
        super.handleFlushComplete(finalFrame);

        if (finalFrame) {
            getChannel().sinkDone();
        }
        if (finalFrame && finishListener != null) {
            finishListener.handleEvent(this);
        }
    }

    @Override
    protected void channelForciblyClosed() throws IOException {
        super.channelForciblyClosed();
        getChannel().sinkDone();
        finishListener.handleEvent(this);
    }

    public void clearHeader() {
        header = null;
    }
}
