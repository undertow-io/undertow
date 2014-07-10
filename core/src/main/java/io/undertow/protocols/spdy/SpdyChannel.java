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

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.protocol.framed.AbstractFramedChannel;
import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.util.Attachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import io.undertow.util.HeaderMap;

import org.xnio.Bits;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * SPDY channel.
 *
 * @author Stuart Douglas
 */
public class SpdyChannel extends AbstractFramedChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> implements Attachable {

    static final int DEFAULT_INITIAL_WINDOW_SIZE = 64 * 1024 * 0124;

    static final int SYN_STREAM = 1;
    static final int SYN_REPLY = 2;
    static final int RST_STREAM = 3;
    static final int SETTINGS = 4;
    static final int PING = 6;
    static final int GOAWAY = 7;
    static final int HEADERS = 8;
    static final int WINDOW_UPDATE = 9;

    public static final int CLOSE_OK = 0;
    public static final int CLOSE_PROTOCOL_ERROR = 1;
    public static final int CLOSE_INTERNAL_ERROR = 2;

    static final int FLAG_FIN = 1;
    static final int FLAG_UNIDIRECTIONAL = 2;
    static final int CONTROL_FRAME = 1 << 31;

    public static final int RST_STATUS_PROTOCOL_ERROR = 1;
    public static final int RST_STATUS_INVALID_STREAM = 2;
    public static final int RST_STATUS_REFUSED_STREAM = 3;
    public static final int RST_STATUS_UNSUPPORTED_VERSION = 4;
    public static final int RST_STATUS_CANCEL = 5;
    public static final int RST_STATUS_INTERNAL_ERROR = 6;
    public static final int RST_STATUS_FLOW_CONTROL_ERROR = 7;
    public static final int RST_STATUS_STREAM_IN_USE = 8;
    public static final int RST_STATUS_STREAM_ALREADY_CLOSED = 9;
    public static final int RST_STATUS_FRAME_TOO_LARGE = 11;

    private final Inflater inflater = new Inflater(false);
    private final Deflater deflater = new Deflater(6);

    private SpdyFrameParser frameParser;
    private final Map<Integer, SpdyStreamSourceChannel> incomingStreams = new ConcurrentHashMap<>();
    private final Map<Integer, SpdyStreamStreamSinkChannel> outgoingStreams = new ConcurrentHashMap<>();

    private volatile int initialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;


    /**
     * How much data we have told the remote endpoint we are prepared to accept.
     */
    private volatile int receiveWindowSize = initialWindowSize;

    /**
     * How much data we can send to the remote endpoint, at the connection level.
     */
    private volatile int sendWindowSize = initialWindowSize;

    private final Pool<ByteBuffer> heapBufferPool;

    private boolean thisGoneAway = false;
    private boolean peerGoneAway = false;

    private int streamIdCounter;
    private int lastGoodStreamId;

    private final Map<AttachmentKey<?>, Object> attachments = Collections.synchronizedMap(new HashMap<AttachmentKey<?>, Object>());

    public SpdyChannel(StreamConnection connectedStreamChannel, Pool<ByteBuffer> bufferPool, Pooled<ByteBuffer> data, Pool<ByteBuffer> heapBufferPool, boolean clientSide) {
        super(connectedStreamChannel, bufferPool, SpdyFramePriority.INSTANCE, data);
        this.heapBufferPool = heapBufferPool;
        this.deflater.setDictionary(SpdyProtocolUtils.SPDY_DICT);
        streamIdCounter = clientSide ? 1 : 2;
    }

    @Override
    protected SpdyStreamSourceChannel createChannel(FrameHeaderData frameHeaderData, Pooled<ByteBuffer> frameData) throws IOException {
        SpdyFrameParser frameParser = (SpdyFrameParser) frameHeaderData;
        SpdyStreamSourceChannel channel;
        if(!frameParser.control) {
            //we only handle control frames here. If a data frame falls through it means that it has been cancelled
            //we just free the data and return null, effectivly dropping the frame
            UndertowLogger.REQUEST_LOGGER.tracef("Dropping Frame of length %s for stream %s", frameParser.getFrameLength(), frameParser.dataFrameStreamId);
            return null;
        }
        //note that not all frame types are covered here, as some are only relevant to already active streams
        //if which case they are handled by the existing channel support
        switch (frameParser.type) {
            case SYN_STREAM: {
                SpdySynStreamParser parser = (SpdySynStreamParser) frameParser.parser;
                channel = new SpdySynStreamStreamSourceChannel(this, frameData, frameHeaderData.getFrameLength(), deflater, parser.getHeaderMap(), parser.streamId);
                lastGoodStreamId = parser.streamId;
                if (!Bits.anyAreSet(frameParser.flags, FLAG_FIN)) {
                    incomingStreams.put(parser.streamId, channel);
                }
                break;
            }
            case SYN_REPLY: {
                SpdySynReplyParser parser = (SpdySynReplyParser) frameParser.parser;
                channel = new SpdySynReplyStreamSourceChannel(this, frameData, frameHeaderData.getFrameLength(), parser.getHeaderMap(), parser.streamId);
                lastGoodStreamId = parser.streamId;
                if (!Bits.anyAreSet(frameParser.flags, FLAG_FIN)) {
                    incomingStreams.put(parser.streamId, channel);
                }
                break;
            }
            case RST_STREAM: {
                SpdyRstStreamParser parser = (SpdyRstStreamParser) frameParser.parser;
                channel = new SpdyRstStreamStreamSourceChannel(this, frameData, frameParser.getFrameLength(), parser.getStreamId());
                handleRstStream(parser.getStreamId());
                break;
            }
            case SETTINGS: {
                updateSettings(((SpdySettingsParser) frameParser.parser).getSettings());
                channel = new SpdySettingsStreamSourceChannel(this, frameData, frameParser.getFrameLength(), ((SpdySettingsParser) frameParser.parser).getSettings());
                break;
            }
            case PING: {
                channel = new SpdyPingStreamSourceChannel(this, frameData, frameParser.getFrameLength(), ((SpdyPingParser) frameParser.parser).getId());
                break;
            }
            case GOAWAY: {
                SpdyGoAwayParser spdyGoAwayParser = (SpdyGoAwayParser) frameParser.parser;
                channel = new SpdyGoAwayStreamSourceChannel(this, frameData, frameParser.getFrameLength(), spdyGoAwayParser.getStatusCode(), spdyGoAwayParser.getLastGoodStreamId());
                peerGoneAway = true;
                break;
            }
            case WINDOW_UPDATE: {
                SpdyWindowUpdateParser parser = (SpdyWindowUpdateParser) frameParser.parser;
                handleWindowUpdate(parser.getStreamId(), parser.getDeltaWindowSize());
                frameData.free();
                //we don't return window update notifications, they are handled internally
                return null;
            }
            default: {
                throw UndertowMessages.MESSAGES.unexpectedFrameType(frameParser.type);
            }
        }
        if (Bits.anyAreSet(frameParser.flags, FLAG_FIN)) {
            channel.lastFrame();
        }
        return channel;
    }

    @Override
    protected FrameHeaderData parseFrame(ByteBuffer data) throws IOException {
        SpdyFrameParser frameParser = this.frameParser;
        if (frameParser == null) {
            this.frameParser = frameParser = new SpdyFrameParser();
        }
        if (!frameParser.handle(data)) {
            return null;
        }
        this.frameParser = null;
        return frameParser;

    }

    protected void lastDataRead() {
        if(!peerGoneAway && !thisGoneAway) {
            //the peer has performed an unclean close
            //we assume something happened to the underlying connection
            //we attempt to send our own GOAWAY, however it will probably fail,
            //which will trigger a forces close of our write side
            sendGoAway(CLOSE_PROTOCOL_ERROR);
            peerGoneAway = true;
        }
    }

    @Override
    public boolean isOpen() {
        return super.isOpen() && !peerGoneAway && !thisGoneAway;
    }

    @Override
    protected boolean isLastFrameReceived() {
        return peerGoneAway;
    }

    @Override
    protected boolean isLastFrameSent() {
        return peerGoneAway || thisGoneAway;
    }

    @Override
    protected void handleBrokenSourceChannel(Throwable e) {
        UndertowLogger.REQUEST_LOGGER.debugf(e, "Closing SPDY channel to %s due to broken read side", getPeerAddress());
        sendGoAway(CLOSE_PROTOCOL_ERROR, new SpdyControlMessageExceptionHandler());
    }

    @Override
    protected void handleBrokenSinkChannel(Throwable e) {
        UndertowLogger.REQUEST_LOGGER.debugf(e, "Closing SPDY channel to %s due to broken write side", getPeerAddress());
        IoUtils.safeClose(this);
    }

    @Override
    protected void closeSubChannels() {
        for (Map.Entry<Integer, SpdyStreamSourceChannel> e : incomingStreams.entrySet()) {
            SpdyStreamSourceChannel receiver = e.getValue();
            if (receiver.isReadResumed()) {
                ChannelListeners.invokeChannelListener(receiver.getIoThread(), receiver, ((ChannelListener.SimpleSetter) receiver.getReadSetter()).get());
            }
            IoUtils.safeClose(receiver);
        }
        incomingStreams.clear();

        for (Map.Entry<Integer, SpdyStreamStreamSinkChannel> e : outgoingStreams.entrySet()) {
            SpdyStreamStreamSinkChannel receiver = e.getValue();
            if (receiver.isWritesShutdown()) {
                ChannelListeners.invokeChannelListener(receiver.getIoThread(), receiver, ((ChannelListener.SimpleSetter) receiver.getWriteSetter()).get());
            }
            IoUtils.safeClose(receiver);
        }
        outgoingStreams.clear();
    }

    /**
     * Setting have been received from the client
     *
     * @param settings
     */
    synchronized void updateSettings(List<SpdySetting> settings) {
        for (SpdySetting setting : settings) {
            if (setting.getId() == SpdySetting.SETTINGS_INITIAL_WINDOW_SIZE) {
                int old = initialWindowSize;
                initialWindowSize = setting.getValue();
                int difference = old - initialWindowSize;
                receiveWindowSize += difference;
                sendWindowSize += difference;
            }
            //ignore the rest for now
        }
    }

    public int getSpdyVersion() {
        return 3;
    }

    Pool<ByteBuffer> getHeapBufferPool() {
        return heapBufferPool;
    }

    int getInitialWindowSize() {
        return initialWindowSize;
    }

    public synchronized void handleWindowUpdate(int streamId, int deltaWindowSize) throws IOException {
        if (streamId == 0) {
            boolean exhausted = sendWindowSize == 0;
            sendWindowSize += deltaWindowSize;
            if(exhausted) {
                notifyFlowControlAllowed();
            }
        } else {
            SpdyStreamStreamSinkChannel stream = outgoingStreams.get(streamId);
            if (stream == null) {
                //TODO: error handling
            } else {
                stream.updateFlowControlWindow(deltaWindowSize);
            }
        }
    }

    synchronized void notifyFlowControlAllowed() throws IOException {
        super.recalculateHeldFrames();
    }

    public void sendPing(int id) {
        sendPing(id, new SpdyControlMessageExceptionHandler());
    }

    public void sendPing(int id, final ChannelExceptionHandler<SpdyStreamSinkChannel> exceptionHandler) {
        SpdyPingStreamSinkChannel ping = new SpdyPingStreamSinkChannel(this, id);
        try {
            ping.shutdownWrites();
            if (!ping.flush()) {
                ping.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, exceptionHandler));
                ping.resumeWrites();
            }
        } catch (IOException e) {
            exceptionHandler.handleException(ping, e);
        }
    }


    public void sendGoAway(int status) {
        sendGoAway(status, new SpdyControlMessageExceptionHandler());
    }

    public void sendGoAway(int status, final ChannelExceptionHandler<SpdyStreamSinkChannel> exceptionHandler) {
        if(thisGoneAway) {
            return;
        }
        thisGoneAway = true;
        SpdyGoAwayStreamSinkChannel goAway = new SpdyGoAwayStreamSinkChannel(this, status, lastGoodStreamId);
        try {
            goAway.shutdownWrites();
            if (!goAway.flush()) {
                goAway.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, exceptionHandler));
                goAway.resumeWrites();
            }
        } catch (IOException e) {
            exceptionHandler.handleException(goAway, e);
        }
    }

    public void sendUpdateWindowSize(int streamId, int delta) {
        SpdyWindowUpdateStreamSinkChannel windowUpdateStreamSinkChannel = new SpdyWindowUpdateStreamSinkChannel(this, streamId, delta);
        try {
            windowUpdateStreamSinkChannel.shutdownWrites();
            if (!windowUpdateStreamSinkChannel.flush()) {
                windowUpdateStreamSinkChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, new SpdyControlMessageExceptionHandler()));
                windowUpdateStreamSinkChannel.resumeWrites();
            }
        } catch (IOException e) {
            handleBrokenSinkChannel(e);
        }

    }

    public SSLSession getSslSession() {
        StreamConnection con = getUnderlyingConnection();
        if (con instanceof SslConnection) {
            return ((SslConnection) con).getSslSession();
        }
        return null;
    }

    public synchronized void updateReceiveFlowControlWindow(int read) {
        if(read <= 0) {
            return;
        }
        receiveWindowSize -= read;
        //TODO: make this configurable, we should be able to set the policy that is used to determine when to update the window size
        int initialWindowSize = this.initialWindowSize;
        if (receiveWindowSize < (initialWindowSize / 2)) {
            int delta = initialWindowSize - receiveWindowSize;
            receiveWindowSize += delta;
            sendUpdateWindowSize(0, delta);
        }
    }

    public synchronized SpdySynStreamStreamSinkChannel createStream(HeaderMap requestHeaders) throws IOException {
        if(!isOpen()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        int streamId = streamIdCounter;
        streamIdCounter += 2;
        SpdySynStreamStreamSinkChannel spdySynStreamStreamSinkChannel = new SpdySynStreamStreamSinkChannel(this, requestHeaders, streamId, deflater);
        outgoingStreams.put(streamId, spdySynStreamStreamSinkChannel);
        return spdySynStreamStreamSinkChannel;

    }

    /**
     * Try and decrement the send window by the given amount of bytes.
     *
     * @param bytesToGrab The amount of bytes the sender is trying to send
     * @return The actual amount of bytes the sender can send
     */
    synchronized int grabFlowControlBytes(int bytesToGrab) {
        int min = Math.min(bytesToGrab, sendWindowSize);
        sendWindowSize -= min;
        return min;
    }

    void registerStreamSink(SpdySynReplyStreamSinkChannel synResponse) {
        outgoingStreams.put(synResponse.getStreamId(), synResponse);
    }

    void removeStreamSink(int streamId) {
        outgoingStreams.remove(streamId);
    }

    public boolean isClient() {
        return streamIdCounter % 2 == 1;
    }

    @Override
    public <T> T getAttachment(AttachmentKey<T> key) {
        if (key == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("key");
        }
        return (T) attachments.get(key);
    }

    @Override
    public <T> List<T> getAttachmentList(AttachmentKey<? extends List<T>> key) {
        if (key == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("key");
        }
        Object o = attachments.get(key);
        if(o == null) {
            return Collections.emptyList();
        }
        return (List)o;
    }

    @Override
    public <T> T putAttachment(AttachmentKey<T> key, T value) {
        if (key == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("key");
        }
        return key.cast(attachments.put(key, key.cast(value)));
    }

    @Override
    public <T> T removeAttachment(AttachmentKey<T> key) {
        return key.cast(attachments.remove(key));
    }

    @Override
    public <T> void addToAttachmentList(AttachmentKey<AttachmentList<T>> key, T value) {

        if (key == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("key");
        }
        final Map<AttachmentKey<?>, Object> attachments = this.attachments;
        synchronized (attachments) {
            final List<T> list = key.cast(attachments.get(key));
            if (list == null) {
                final AttachmentList<T> newList = new AttachmentList<T>((Class<T>) Object.class);
                attachments.put(key, newList);
                newList.add(value);
            } else {
                list.add(value);
            }
        }
    }

    public void sendRstStream(int streamId, int statusCode) {
        handleRstStream(streamId);
        try {
            SpdyRstStreamSinkChannel channel = new SpdyRstStreamSinkChannel(this, streamId, statusCode);
            channel.shutdownWrites();
            if (!channel.flush()) {
                channel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, new ChannelExceptionHandler<SpdyStreamSinkChannel>() {
                    @Override
                    public void handleException(SpdyStreamSinkChannel channel, IOException exception) {
                        markWritesBroken(exception);
                    }
                }));
                channel.resumeWrites();
            }
        } catch (IOException e) {
            markWritesBroken(e);
        }
    }

    private void handleRstStream(int streamId) {
        SpdyStreamSourceChannel incoming = incomingStreams.remove(streamId);
        if(incoming != null) {
            incoming.rstStream();
        }
        SpdyStreamStreamSinkChannel outgoing = outgoingStreams.remove(streamId);
        if(outgoing != null) {
            outgoing.rstStream();
        }
    }


    class SpdyFrameParser implements FrameHeaderData {

        final byte[] header = new byte[8];
        int read = 0;
        boolean control;

        //control fields
        int version;
        int type;

        //data fields
        int dataFrameStreamId;

        int flags;
        int length;

        SpdyPushBackParser parser = null;

        private static final int CONTROL_MASK = 1 << 7;

        public boolean handle(final ByteBuffer byteBuffer) throws IOException {
            if (parser == null) {
                if (!parseFrameHeader(byteBuffer)) {
                    return false;
                }
                if (!control) {
                    return true;
                }
                switch (type) {
                    case SYN_STREAM: {
                        parser = new SpdySynStreamParser(getBufferPool(), SpdyChannel.this, length, inflater);
                        break;
                    }
                    case RST_STREAM: {
                        parser = new SpdyRstStreamParser(getBufferPool(), length);
                        break;
                    }
                    case HEADERS: {
                        parser = new SpdyHeadersParser(getBufferPool(), SpdyChannel.this, length, inflater);
                        break;
                    }
                    case SYN_REPLY: {
                        parser = new SpdySynReplyParser(getBufferPool(), SpdyChannel.this, length, inflater);
                        break;
                    }
                    case GOAWAY: {
                        parser = new SpdyGoAwayParser(getBufferPool(), length);
                        peerGoneAway = true;
                        break;
                    }
                    case PING: {
                        parser = new SpdyPingParser(getBufferPool(), length);
                        break;
                    }
                    case SETTINGS: {
                        parser = new SpdySettingsParser(length);
                        break;
                    }
                    case WINDOW_UPDATE: {
                        parser = new SpdyWindowUpdateParser(length);
                        break;
                    }
                    default: {
                        return true;
                    }
                }
            }
            parser.parse(byteBuffer);
            return parser.isFinished();
        }

        private boolean parseFrameHeader(ByteBuffer byteBuffer) {
            while (read < 8 && byteBuffer.hasRemaining()) {
                header[read++] = byteBuffer.get();
            }
            if (read != 8) {
                return false;
            }
            control = (header[0] & CONTROL_MASK) != 0;
            if (control) {
                version = (header[0] & ~CONTROL_MASK & 0xFF) << 8;
                version += header[1] & 0xff;
                type = (header[2] & 0xff) << 8;
                type += header[3] & 0xff;
            } else {
                dataFrameStreamId = (header[0] & ~CONTROL_MASK & 0xFF) << 24;
                dataFrameStreamId += (header[1] & 0xff) << 16;
                dataFrameStreamId += (header[2] & 0xff) << 8;
                dataFrameStreamId += header[3] & 0xff;
            }
            flags = header[4] & 0xff;
            length = (header[5] & 0xff) << 16;
            length = (header[6] & 0xff) << 8;
            length += header[7] & 0xff;
            return true;
        }

        @Override
        public long getFrameLength() {
            //control frames have no data
            //we fully parse them as part of the receive process so they are considered to have a length of zero
            if (control) {
                return 0;
            }
            return length;
        }

        @Override
        public AbstractFramedStreamSourceChannel<?, ?, ?> getExistingChannel() {
            if (type == SYN_STREAM) {
                return null;
            }
            int id;
            if (control) {
                id = parser.getStreamId();
                if (id == -1) {
                    return null;
                }
            } else {
                id = dataFrameStreamId;
            }
            //TODO: error
            if (Bits.anyAreSet(flags, FLAG_FIN)) {
                return incomingStreams.remove(id);
            } else {
                return incomingStreams.get(id);
            }
        }
    }

    private class SpdyControlMessageExceptionHandler implements ChannelExceptionHandler<SpdyStreamSinkChannel> {
        @Override
        public void handleException(SpdyStreamSinkChannel channel, IOException exception) {
            IoUtils.safeClose(channel);
            handleBrokenSinkChannel(exception);
        }
    }
}
