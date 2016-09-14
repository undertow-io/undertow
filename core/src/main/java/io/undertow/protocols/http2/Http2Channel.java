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

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.protocol.framed.AbstractFramedChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.server.protocol.http2.Http2OpenListener;
import io.undertow.util.Attachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import io.undertow.util.HeaderMap;
import org.xnio.Bits;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import io.undertow.util.HttpString;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.ssl.SslConnection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLSession;

/**
 * HTTP2 channel.
 *
 * @author Stuart Douglas
 */
public class Http2Channel extends AbstractFramedChannel<Http2Channel, AbstractHttp2StreamSourceChannel, AbstractHttp2StreamSinkChannel> implements Attachable {

    public static final String CLEARTEXT_UPGRADE_STRING = "h2c";


    public static final HttpString METHOD = new HttpString(":method");
    public static final HttpString PATH = new HttpString(":path");
    public static final HttpString SCHEME = new HttpString(":scheme");
    public static final HttpString AUTHORITY = new HttpString(":authority");
    public static final HttpString STATUS = new HttpString(":status");

    static final int FRAME_TYPE_DATA = 0x00;
    static final int FRAME_TYPE_HEADERS = 0x01;
    static final int FRAME_TYPE_PRIORITY = 0x02;
    static final int FRAME_TYPE_RST_STREAM = 0x03;
    static final int FRAME_TYPE_SETTINGS = 0x04;
    static final int FRAME_TYPE_PUSH_PROMISE = 0x05;
    static final int FRAME_TYPE_PING = 0x06;
    static final int FRAME_TYPE_GOAWAY = 0x07;
    static final int FRAME_TYPE_WINDOW_UPDATE = 0x08;
    static final int FRAME_TYPE_CONTINUATION = 0x09;


    public static final int ERROR_NO_ERROR = 0x00;
    public static final int ERROR_PROTOCOL_ERROR = 0x01;
    public static final int ERROR_INTERNAL_ERROR = 0x02;
    public static final int ERROR_FLOW_CONTROL_ERROR = 0x03;
    public static final int ERROR_SETTINGS_TIMEOUT = 0x04;
    public static final int ERROR_STREAM_CLOSED = 0x05;
    public static final int ERROR_FRAME_SIZE_ERROR = 0x06;
    public static final int ERROR_REFUSED_STREAM = 0x07;
    public static final int ERROR_CANCEL = 0x08;
    public static final int ERROR_COMPRESSION_ERROR = 0x09;
    public static final int ERROR_CONNECT_ERROR = 0x0a;
    public static final int ERROR_ENHANCE_YOUR_CALM = 0x0b;
    public static final int ERROR_INADEQUATE_SECURITY = 0x0c;

    static final int DATA_FLAG_END_STREAM = 0x1;
    static final int DATA_FLAG_END_SEGMENT = 0x2;
    static final int DATA_FLAG_PADDED = 0x8;

    static final int PING_FRAME_LENGTH = 8;
    static final int PING_FLAG_ACK = 0x1;

    static final int HEADERS_FLAG_END_STREAM = 0x1;
    static final int HEADERS_FLAG_END_SEGMENT = 0x2;
    static final int HEADERS_FLAG_END_HEADERS = 0x4;
    static final int HEADERS_FLAG_PADDED = 0x8;
    static final int HEADERS_FLAG_PRIORITY = 0x20;

    static final int SETTINGS_FLAG_ACK = 0x1;

    static final int CONTINUATION_FLAG_END_HEADERS = 0x4;

    static final int DEFAULT_INITIAL_WINDOW_SIZE = 65535;

    static final byte[] PREFACE_BYTES = {
            0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54,
            0x54, 0x50, 0x2f, 0x32, 0x2e, 0x30, 0x0d, 0x0a,
            0x0d, 0x0a, 0x53, 0x4d, 0x0d, 0x0a, 0x0d, 0x0a};
    public static final int DEFAULT_MAX_FRAME_SIZE = 16384;
    public static final int MAX_FRAME_SIZE = 16777215;
    public static final int FLOW_CONTROL_MIN_WINDOW = 2;


    private Http2FrameHeaderParser frameParser;
    private final Map<Integer, StreamHolder> currentStreams = new ConcurrentHashMap<>();
    private final String protocol;

    //local
    private int encoderHeaderTableSize;
    private boolean pushEnabled;
    private volatile int initialSendWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    private volatile int initialReceiveWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    private int sendMaxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    private int receiveMaxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    private int unackedReceiveMaxFrameSize = DEFAULT_MAX_FRAME_SIZE; //the old max frame size, this gets updated when our setting frame is acked

    /**
     * How much data we have told the remote endpoint we are prepared to accept.
     */
    private volatile int receiveWindowSize = initialReceiveWindowSize;

    /**
     * How much data we can send to the remote endpoint, at the connection level.
     */
    private volatile long sendWindowSize = initialSendWindowSize;

    private boolean thisGoneAway = false;
    private boolean peerGoneAway = false;
    private boolean lastDataRead = false;

    private int streamIdCounter;
    private int lastGoodStreamId;

    private final HpackDecoder decoder;
    private final HpackEncoder encoder;
    private final int maxPadding;
    private final Random paddingRandom;

    private int prefaceCount;
    private boolean initialSettingsReceived; //settings frame must be the first frame we relieve
    private Http2HeadersParser continuationParser = null; //state for continuation frames
    /**
     * We send the settings frame lazily, which is basically a big hack to work around broken IE support for push (it
     * dies if you send a settings frame with push enabled).
     *
     * Once IE is no longer broken this should be removed.
     */
    private boolean initialSettingsSent = false;

    private final Map<AttachmentKey<?>, Object> attachments = Collections.synchronizedMap(new HashMap<AttachmentKey<?>, Object>());


    public Http2Channel(StreamConnection connectedStreamChannel, String protocol, ByteBufferPool bufferPool, PooledByteBuffer data, boolean clientSide, boolean fromUpgrade, OptionMap settings) {
        this(connectedStreamChannel, protocol, bufferPool, data, clientSide, fromUpgrade, true, null, settings);
    }
    public Http2Channel(StreamConnection connectedStreamChannel, String protocol, ByteBufferPool bufferPool, PooledByteBuffer data, boolean clientSide, boolean fromUpgrade, boolean prefaceRequired, OptionMap settings) {
        this(connectedStreamChannel, protocol, bufferPool, data, clientSide, fromUpgrade, prefaceRequired, null, settings);
    }

    public Http2Channel(StreamConnection connectedStreamChannel, String protocol, ByteBufferPool bufferPool, PooledByteBuffer data, boolean clientSide, boolean fromUpgrade, boolean prefaceRequired, ByteBuffer initialOtherSideSettings, OptionMap settings) {
        super(connectedStreamChannel, bufferPool, Http2FramePriority.INSTANCE, data, settings);
        streamIdCounter = clientSide ? (fromUpgrade ? 3 : 1) : 2;

        pushEnabled = settings.get(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH, true);
        this.initialReceiveWindowSize = settings.get(UndertowOptions.HTTP2_SETTINGS_INITIAL_WINDOW_SIZE, DEFAULT_INITIAL_WINDOW_SIZE);

        this.protocol = protocol == null ? Http2OpenListener.HTTP2 : protocol;

        encoderHeaderTableSize = settings.get(UndertowOptions.HTTP2_SETTINGS_HEADER_TABLE_SIZE, Hpack.DEFAULT_TABLE_SIZE);
        receiveMaxFrameSize = settings.get(UndertowOptions.HTTP2_SETTINGS_MAX_FRAME_SIZE, DEFAULT_MAX_FRAME_SIZE);
        maxPadding = settings.get(UndertowOptions.HTTP2_PADDING_SIZE, 0);
        if(maxPadding > 0) {
            paddingRandom = new SecureRandom();
        } else {
            paddingRandom = null;
        }

        this.decoder = new HpackDecoder(Hpack.DEFAULT_TABLE_SIZE);
        this.encoder = new HpackEncoder(encoderHeaderTableSize);
        if(!prefaceRequired) {
            prefaceCount = PREFACE_BYTES.length;
        }

        if (clientSide) {
            sendPreface();
            prefaceCount = PREFACE_BYTES.length;
            sendSettings();
            initialSettingsSent = true;
            if(fromUpgrade) {
                StreamHolder streamHolder = new StreamHolder((Http2StreamSinkChannel) null);
                streamHolder.sinkClosed = true;
                currentStreams.put(1, streamHolder);
            }
        } else if(fromUpgrade) {
            sendSettings();
            initialSettingsSent = true;
        }
        if (initialOtherSideSettings != null) {
            Http2SettingsParser parser = new Http2SettingsParser(initialOtherSideSettings.remaining());
            try {
                final Http2FrameHeaderParser headerParser = new Http2FrameHeaderParser(this, null);
                headerParser.length = initialOtherSideSettings.remaining();
                parser.parse(initialOtherSideSettings, headerParser);
                updateSettings(parser.getSettings());
            } catch (IOException e) {
                IoUtils.safeClose(connectedStreamChannel);
                //should never happen
                throw new RuntimeException(e);
            }
        }
    }

    private void sendSettings() {
        List<Http2Setting> settings = new ArrayList<>();
        settings.add(new Http2Setting(Http2Setting.SETTINGS_HEADER_TABLE_SIZE, encoderHeaderTableSize));
        if(isClient()) {
            settings.add(new Http2Setting(Http2Setting.SETTINGS_ENABLE_PUSH, pushEnabled ? 1 : 0));
        }
        settings.add(new Http2Setting(Http2Setting.SETTINGS_MAX_FRAME_SIZE, receiveMaxFrameSize));
        settings.add(new Http2Setting(Http2Setting.SETTINGS_INITIAL_WINDOW_SIZE, initialReceiveWindowSize));
        Http2SettingsStreamSinkChannel stream = new Http2SettingsStreamSinkChannel(this, settings);
        flushChannelIgnoreFailure(stream);
    }

    private void sendSettingsAck() {
        if(!initialSettingsSent) {
            sendSettings();
            initialSettingsSent = true;
        }
        Http2SettingsStreamSinkChannel stream = new Http2SettingsStreamSinkChannel(this);
        flushChannelIgnoreFailure(stream);
    }

    private void flushChannelIgnoreFailure(StreamSinkChannel stream) {
        try {
            flushChannel(stream);
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
        }
    }

    private void flushChannel(StreamSinkChannel stream) throws IOException {
        stream.shutdownWrites();
        if (!stream.flush()) {
            stream.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, writeExceptionHandler()));
            stream.resumeWrites();
        }
    }



    private void sendPreface() {
        Http2PrefaceStreamSinkChannel preface = new Http2PrefaceStreamSinkChannel(this);
        flushChannelIgnoreFailure(preface);
    }

    @Override
    protected AbstractHttp2StreamSourceChannel createChannel(FrameHeaderData frameHeaderData, PooledByteBuffer frameData) throws IOException {

        Http2FrameHeaderParser frameParser = (Http2FrameHeaderParser) frameHeaderData;
        AbstractHttp2StreamSourceChannel channel;
        if (frameParser.type == FRAME_TYPE_DATA) {
            //DATA frames must be already associated with a connection. If it gets here then something is wrong
            if (frameParser.streamId == 0 || isIdle(frameParser.streamId)) {
                //spec explicitly calls this out as a connection error
                sendGoAway(ERROR_PROTOCOL_ERROR);
            } else {
                //the spec says we may send the RST_STREAM in this situation, it seems safest to do so
                sendRstStream(frameParser.streamId, ERROR_STREAM_CLOSED);
            }
            UndertowLogger.REQUEST_LOGGER.tracef("Dropping Frame of length %s for stream %s", frameParser.getFrameLength(), frameParser.streamId);
            return null;
        }
        //note that not all frame types are covered here, as some are only relevant to already active streams
        //if which case they are handled by the existing channel support
        switch (frameParser.type) {

            case FRAME_TYPE_CONTINUATION:
            case FRAME_TYPE_PUSH_PROMISE: {
                //this is some 'clever' code to deal with both types continuation (push_promise and headers)
                //if the continuation is not a push promise it falls through to the headers code
                if(frameParser.parser instanceof Http2PushPromiseParser) {
                    if(!isClient()) {
                        sendGoAway(ERROR_PROTOCOL_ERROR);
                        throw UndertowMessages.MESSAGES.serverReceivedPushPromise();
                    }
                    Http2PushPromiseParser pushPromiseParser = (Http2PushPromiseParser) frameParser.parser;
                    channel = new Http2PushPromiseStreamSourceChannel(this, frameData, frameParser.getFrameLength(), pushPromiseParser.getHeaderMap(), pushPromiseParser.getPromisedStreamId(), frameParser.streamId);
                    break;
                }
                //fall through
            }
            case FRAME_TYPE_HEADERS: {
                if(!isIdle(frameParser.streamId)) {
                    //this is an existing stream
                    //make sure it exists
                    StreamHolder existing = currentStreams.get(frameParser.streamId);
                    if(existing == null || existing.sourceClosed) {
                        sendRstStream(frameParser.streamId, ERROR_STREAM_CLOSED);
                        frameData.close();
                        return null;
                    } else if (existing.sourceChannel != null ){
                        //if exists
                        //make sure it has END_STREAM set
                        if(!Bits.allAreSet(frameParser.flags, HEADERS_FLAG_END_STREAM)) {
                            sendGoAway(ERROR_PROTOCOL_ERROR);
                            frameData.close();
                            return null;
                        }
                    }
                } else {
                    if(frameParser.streamId % 2 == (isClient() ? 1 : 0)) {
                        sendGoAway(ERROR_PROTOCOL_ERROR);
                        frameData.close();
                        return null;
                    }
                }
                Http2HeadersParser parser = (Http2HeadersParser) frameParser.parser;

                channel = new Http2StreamSourceChannel(this, frameData, frameHeaderData.getFrameLength(), parser.getHeaderMap(), frameParser.streamId);
                lastGoodStreamId = Math.max(lastGoodStreamId, frameParser.streamId);

                StreamHolder holder = currentStreams.get(frameParser.streamId);
                if(holder == null) {
                    currentStreams.put(frameParser.streamId, holder = new StreamHolder((Http2StreamSourceChannel) channel));
                } else {
                    holder.sourceChannel = (Http2StreamSourceChannel) channel;
                }
                if (parser.isHeadersEndStream() && Bits.allAreSet(frameParser.flags, HEADERS_FLAG_END_HEADERS)) {
                    channel.lastFrame();
                    holder.sourceChannel = null;
                    //this is yuck
                    if(!isClient() || !"100".equals(parser.getHeaderMap().getFirst(STATUS))) {
                        holder.sourceClosed = true;
                    }
                }
                if(parser.isInvalid()) {
                    channel.rstStream(ERROR_PROTOCOL_ERROR);
                    sendRstStream(frameParser.streamId, Http2Channel.ERROR_PROTOCOL_ERROR);
                    channel = null;
                }
                if(parser.getDependentStreamId() == frameParser.streamId) {
                    sendRstStream(frameParser.streamId, ERROR_PROTOCOL_ERROR);
                    frameData.close();
                    return null;
                }
//                if(priorityTree != null) {
//                    priorityTree.registerStream(frameParser.streamId, parser.getDependentStreamId(), parser.getWeight(), parser.isExclusive());
//                }
                break;
            }
            case FRAME_TYPE_RST_STREAM: {
                Http2RstStreamParser parser = (Http2RstStreamParser) frameParser.parser;
                if (frameParser.streamId == 0) {
                    if(frameData != null) {
                        frameData.close();
                    }
                    throw new ConnectionErrorException(Http2Channel.ERROR_PROTOCOL_ERROR, UndertowMessages.MESSAGES.streamIdMustNotBeZeroForFrameType(FRAME_TYPE_RST_STREAM));
                }
                channel = new Http2RstStreamStreamSourceChannel(this, frameData, parser.getErrorCode(), frameParser.streamId);
                handleRstStream(frameParser.streamId);
                if(isIdle(frameParser.streamId)) {
                    sendGoAway(ERROR_PROTOCOL_ERROR);
                }
                break;
            }
            case FRAME_TYPE_SETTINGS: {
                if (!Bits.anyAreSet(frameParser.flags, SETTINGS_FLAG_ACK)) {
                    if(updateSettings(((Http2SettingsParser) frameParser.parser).getSettings())) {
                        sendSettingsAck();
                    }
                } else if (frameHeaderData.getFrameLength() != 0) {
                    sendGoAway(ERROR_FRAME_SIZE_ERROR);
                    frameData.close();
                    return null;
                }
                channel = new Http2SettingsStreamSourceChannel(this, frameData, frameParser.getFrameLength(), ((Http2SettingsParser) frameParser.parser).getSettings());
                unackedReceiveMaxFrameSize = receiveMaxFrameSize;
                break;
            }
            case FRAME_TYPE_PING: {
                Http2PingParser pingParser = (Http2PingParser) frameParser.parser;
                frameData.close();
                boolean ack = Bits.anyAreSet(frameParser.flags, PING_FLAG_ACK);
                channel = new Http2PingStreamSourceChannel(this, pingParser.getData(), ack);
                if(!ack) { //not an ack from one of our pings, so send it back
                    sendPing(pingParser.getData(), null, true);
                }
                break;
            }
            case FRAME_TYPE_GOAWAY: {
                Http2GoAwayParser http2GoAwayParser = (Http2GoAwayParser) frameParser.parser;
                channel = new Http2GoAwayStreamSourceChannel(this, frameData, frameParser.getFrameLength(), http2GoAwayParser.getStatusCode(), http2GoAwayParser.getLastGoodStreamId());
                peerGoneAway = true;
                //the peer is going away
                //everything is broken
                for(StreamHolder holder : currentStreams.values()) {
                    if(holder.sourceChannel != null) {
                        holder.sourceChannel.rstStream();
                    }
                    if(holder.sinkChannel != null) {
                        holder.sinkChannel.rstStream();
                    }
                }
                frameData.close();
                break;
            }
            case FRAME_TYPE_WINDOW_UPDATE: {
                Http2WindowUpdateParser parser = (Http2WindowUpdateParser) frameParser.parser;
                handleWindowUpdate(frameParser.streamId, parser.getDeltaWindowSize());
                frameData.close();
                //we don't return window update notifications, they are handled internally
                return null;
            }
            case FRAME_TYPE_PRIORITY: {
                Http2PriorityParser parser = (Http2PriorityParser) frameParser.parser;
                if(parser.getStreamDependency() == frameParser.streamId) {
                    //according to the spec this is a stream error
                    sendRstStream(frameParser.streamId, ERROR_PROTOCOL_ERROR);
                    return null;
                }
                frameData.close();
//                if(priorityTree == null) {
//                    //we don't care, because we are the client side
//                    //so this situation should never happen
//                    return null;
//                }
//                priorityTree.priorityFrame(frameParser.streamId, parser.getStreamDependency(), parser.getWeight(), parser.isExclusive());
//                //we don't return priority notifications, they are handled internally
                return null;
            }
            default: {
                UndertowLogger.REQUEST_LOGGER.tracef("Dropping frame of length %s and type %s for stream %s as we do not understand this type of frame", frameParser.getFrameLength(), frameParser.type, frameParser.streamId);
                frameData.close();
                return null;
            }
        }
        return channel;
    }

    @Override
    protected FrameHeaderData parseFrame(ByteBuffer data) throws IOException {
        if (prefaceCount < PREFACE_BYTES.length) {
            while (data.hasRemaining() && prefaceCount < PREFACE_BYTES.length) {
                if (data.get() != PREFACE_BYTES[prefaceCount]) {
                    IoUtils.safeClose(getUnderlyingConnection());
                    throw UndertowMessages.MESSAGES.incorrectHttp2Preface();
                }
                prefaceCount++;
            }
        }
        Http2FrameHeaderParser frameParser = this.frameParser;
        if (frameParser == null) {
            this.frameParser = frameParser = new Http2FrameHeaderParser(this, continuationParser);
            this.continuationParser = null;
        }
        if (!frameParser.handle(data)) {
            return null;
        }
        if (!initialSettingsReceived) {
            if (frameParser.type != FRAME_TYPE_SETTINGS) {
                UndertowLogger.REQUEST_IO_LOGGER.remoteEndpointFailedToSendInitialSettings(frameParser.type);
                //StringBuilder sb = new StringBuilder();
                //while (data.hasRemaining()) {
                //    sb.append((char)data.get());
                //    sb.append(" ");
                //}
                markReadsBroken(new IOException());
            } else {
                initialSettingsReceived = true;
            }
        }
        this.frameParser = null;
        if (frameParser.getFrameLength() > receiveMaxFrameSize && frameParser.getFrameLength() > unackedReceiveMaxFrameSize) {
            sendGoAway(ERROR_FRAME_SIZE_ERROR);
            throw UndertowMessages.MESSAGES.http2FrameTooLarge();
        }
        if (frameParser.getContinuationParser() != null) {
            this.continuationParser = frameParser.getContinuationParser();
            return null;
        }
        return frameParser;

    }

    protected void lastDataRead() {
        lastDataRead = true;
        if(!peerGoneAway && !thisGoneAway) {
            //the peer has performed an unclean close
            //if they have streams that are still expecting data then this is an error condition
            if(currentStreams.size() > 0) {
                //we assume something happened to the underlying connection
                //we attempt to send our own GOAWAY, however it will probably fail,
                //which will trigger a forces close of our write side
                sendGoAway(ERROR_CONNECT_ERROR);
            } else {
                //we just close the connection, as the peer has performed an unclean close
                IoUtils.safeClose(this);
            }
            peerGoneAway = true;
        }
    }

    @Override
    protected boolean isLastFrameReceived() {
        return lastDataRead;
    }

    @Override
    protected boolean isLastFrameSent() {
        return thisGoneAway;
    }

    @Override
    protected void handleBrokenSourceChannel(Throwable e) {
        UndertowLogger.REQUEST_LOGGER.debugf(e, "Closing HTTP2 channel to %s due to broken read side", getPeerAddress());
        if (e instanceof ConnectionErrorException) {
            sendGoAway(((ConnectionErrorException) e).getCode(), new Http2ControlMessageExceptionHandler());
        } else {
            sendGoAway(e instanceof ClosedChannelException ? Http2Channel.ERROR_CONNECT_ERROR : Http2Channel.ERROR_PROTOCOL_ERROR, new Http2ControlMessageExceptionHandler());
        }
    }

    @Override
    protected void handleBrokenSinkChannel(Throwable e) {
        UndertowLogger.REQUEST_LOGGER.debugf(e, "Closing HTTP2 channel to %s due to broken write side", getPeerAddress());
        //the write side is broken, so we can't even send GO_AWAY
        //just tear down the TCP connection
        IoUtils.safeClose(this);
    }

    @Override
    protected void closeSubChannels() {

        for (Map.Entry<Integer, StreamHolder> e : currentStreams.entrySet()) {
            StreamHolder holder = e.getValue();
            AbstractHttp2StreamSourceChannel receiver = holder.sourceChannel;
            if(receiver != null) {
                if (receiver.isReadResumed()) {
                    ChannelListeners.invokeChannelListener(receiver.getIoThread(), receiver, ((ChannelListener.SimpleSetter) receiver.getReadSetter()).get());
                }
                IoUtils.safeClose(receiver);
            }
            Http2StreamSinkChannel sink = holder.sinkChannel;
            if(sink != null) {
                if (sink.isWritesShutdown()) {
                    ChannelListeners.invokeChannelListener(sink.getIoThread(), sink, ((ChannelListener.SimpleSetter) sink.getWriteSetter()).get());
                }
                IoUtils.safeClose(sink);
            }

        }
    }

    /**
     * Setting have been received from the client
     *
     * @param settings
     */
    synchronized boolean updateSettings(List<Http2Setting> settings) {
        for (Http2Setting setting : settings) {
            if (setting.getId() == Http2Setting.SETTINGS_INITIAL_WINDOW_SIZE) {
                int old = initialSendWindowSize;
                if(setting.getValue() > Integer.MAX_VALUE) {
                    sendGoAway(ERROR_FLOW_CONTROL_ERROR);
                    return false;
                }
                initialSendWindowSize = (int) setting.getValue();
                int difference = initialSendWindowSize - old;
                sendWindowSize += difference;
            } else if (setting.getId() == Http2Setting.SETTINGS_MAX_FRAME_SIZE) {
                if(setting.getValue() > MAX_FRAME_SIZE || setting.getValue() < DEFAULT_MAX_FRAME_SIZE) {
                    UndertowLogger.REQUEST_IO_LOGGER.debug("Invalid value received for SETTINGS_MAX_FRAME_SIZE " + setting.getValue());
                    sendGoAway(ERROR_PROTOCOL_ERROR);
                    return false;
                }
                sendMaxFrameSize = (int) setting.getValue();
            } else if (setting.getId() == Http2Setting.SETTINGS_HEADER_TABLE_SIZE) {
                encoder.setMaxTableSize((int) setting.getValue());
            } else if (setting.getId() == Http2Setting.SETTINGS_ENABLE_PUSH) {

                int result = (int) setting.getValue();
                //we allow the remote endpoint to disable push
                //but not enable it if it has been explictly disabled on this side
                if(result == 0) {
                    pushEnabled = false;
                } else if(result != 1) {
                    //invalid value
                    UndertowLogger.REQUEST_IO_LOGGER.debug("Invalid value received for SETTINGS_ENABLE_PUSH " + result);
                    sendGoAway(ERROR_PROTOCOL_ERROR);
                    return false;
                }
            }
            //ignore the rest for now
        }
        return true;
    }

    public int getHttp2Version() {
        return 3;
    }

    public int getInitialSendWindowSize() {
        return initialSendWindowSize;
    }

    public int getInitialReceiveWindowSize() {
        return initialReceiveWindowSize;
    }

    public synchronized void handleWindowUpdate(int streamId, int deltaWindowSize) throws IOException {
        if (streamId == 0) {
            if (deltaWindowSize == 0) {
                UndertowLogger.REQUEST_IO_LOGGER.debug("Invalid flow-control window increment of 0 received with WINDOW_UPDATE frame for connection");
                sendGoAway(ERROR_PROTOCOL_ERROR);
                return;
            }

            boolean exhausted = sendWindowSize <= FLOW_CONTROL_MIN_WINDOW; //

            sendWindowSize += deltaWindowSize;
            if (exhausted) {
                notifyFlowControlAllowed();
            }
            if(sendWindowSize > Integer.MAX_VALUE) {
                sendGoAway(ERROR_FLOW_CONTROL_ERROR);
            }
        } else {
            if (deltaWindowSize == 0) {
                UndertowLogger.REQUEST_IO_LOGGER.debug("Invalid flow-control window increment of 0 received with WINDOW_UPDATE frame for stream " + streamId);
                sendRstStream(streamId, ERROR_PROTOCOL_ERROR);
                return;
            }
            StreamHolder holder = currentStreams.get(streamId);
            Http2StreamSinkChannel stream = holder != null ? holder.sinkChannel : null;
            if (stream == null) {
                if(isIdle(streamId)) {
                    sendGoAway(ERROR_PROTOCOL_ERROR);
                }
            } else {
                stream.updateFlowControlWindow(deltaWindowSize);
            }
        }
    }

    synchronized void notifyFlowControlAllowed() throws IOException {
        super.recalculateHeldFrames();
    }

    public void sendPing(byte[] data) {
        sendPing(data, new Http2ControlMessageExceptionHandler());
    }

    public void sendPing(byte[] data, final ChannelExceptionHandler<AbstractHttp2StreamSinkChannel> exceptionHandler) {
        sendPing(data, exceptionHandler, false);
    }

    void sendPing(byte[] data, final ChannelExceptionHandler<AbstractHttp2StreamSinkChannel> exceptionHandler, boolean ack) {
        Http2PingStreamSinkChannel ping = new Http2PingStreamSinkChannel(this, data, ack);
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
        sendGoAway(status, new Http2ControlMessageExceptionHandler());
    }

    public void sendGoAway(int status, final ChannelExceptionHandler<AbstractHttp2StreamSinkChannel> exceptionHandler) {
        if (thisGoneAway) {
            return;
        }
        thisGoneAway = true;
        Http2GoAwayStreamSinkChannel goAway = new Http2GoAwayStreamSinkChannel(this, status, lastGoodStreamId);
        try {
            goAway.shutdownWrites();
            if (!goAway.flush()) {
                goAway.getWriteSetter().set(ChannelListeners.flushingChannelListener(new ChannelListener<Channel>() {
                    @Override
                    public void handleEvent(Channel channel) {
                        IoUtils.safeClose(Http2Channel.this);
                    }
                }, exceptionHandler));
                goAway.resumeWrites();
            } else {
                IoUtils.safeClose(this);
            }
        } catch (IOException e) {
            exceptionHandler.handleException(goAway, e);
        }
    }

    public void sendUpdateWindowSize(int streamId, int delta) throws IOException {
        Http2WindowUpdateStreamSinkChannel windowUpdateStreamSinkChannel = new Http2WindowUpdateStreamSinkChannel(this, streamId, delta);
        flushChannel(windowUpdateStreamSinkChannel);

    }

    public SSLSession getSslSession() {
        StreamConnection con = getUnderlyingConnection();
        if (con instanceof SslConnection) {
            return ((SslConnection) con).getSslSession();
        }
        return null;
    }

    public synchronized void updateReceiveFlowControlWindow(int read) throws IOException {
        if (read <= 0) {
            return;
        }
        receiveWindowSize -= read;
        //TODO: make this configurable, we should be able to set the policy that is used to determine when to update the window size
        int initialWindowSize = this.initialReceiveWindowSize;
        if (receiveWindowSize < (initialWindowSize / 2)) {
            int delta = initialWindowSize - receiveWindowSize;
            receiveWindowSize += delta;
            sendUpdateWindowSize(0, delta);
        }
    }

    /**
     * Creates a strema using a HEADERS frame
     *
     * @param requestHeaders
     * @return
     * @throws IOException
     */
    public synchronized Http2HeadersStreamSinkChannel createStream(HeaderMap requestHeaders) throws IOException {
        if (!isClient()) {
            throw UndertowMessages.MESSAGES.headersStreamCanOnlyBeCreatedByClient();
        }
        if (!isOpen()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        int streamId = streamIdCounter;
        streamIdCounter += 2;
        Http2HeadersStreamSinkChannel http2SynStreamStreamSinkChannel = new Http2HeadersStreamSinkChannel(this, streamId, requestHeaders);
        currentStreams.put(streamId, new StreamHolder(http2SynStreamStreamSinkChannel));
        return http2SynStreamStreamSinkChannel;
    }

    public synchronized Http2HeadersStreamSinkChannel sendPushPromise(int associatedStreamId, HeaderMap requestHeaders, HeaderMap responseHeaders) throws IOException {
        if (!isOpen()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        if (isClient()) {
            throw UndertowMessages.MESSAGES.pushPromiseCanOnlyBeCreatedByServer();
        }
        int streamId = streamIdCounter;
        streamIdCounter += 2;
        Http2PushPromiseStreamSinkChannel pushPromise = new Http2PushPromiseStreamSinkChannel(this, requestHeaders, associatedStreamId, streamId);
        flushChannel(pushPromise);

        Http2HeadersStreamSinkChannel http2SynStreamStreamSinkChannel = new Http2HeadersStreamSinkChannel(this, streamId, responseHeaders);
        currentStreams.put(streamId, new StreamHolder(http2SynStreamStreamSinkChannel));
        return http2SynStreamStreamSinkChannel;
    }

    /**
     * Try and decrement the send window by the given amount of bytes.
     *
     * @param bytesToGrab The amount of bytes the sender is trying to send
     * @return The actual amount of bytes the sender can send
     */
    synchronized int grabFlowControlBytes(int bytesToGrab) {
        int min = (int) Math.min(bytesToGrab, sendWindowSize);
        if(bytesToGrab > FLOW_CONTROL_MIN_WINDOW && min <= FLOW_CONTROL_MIN_WINDOW) {
            //this can cause problems with padding, so we just return 0
            return 0;
        }
        min = Math.min(sendMaxFrameSize, min);
        sendWindowSize -= min;
        return min;
    }

    void registerStreamSink(Http2HeadersStreamSinkChannel synResponse) {
        StreamHolder existing = currentStreams.get(synResponse.getStreamId());
        if(existing == null) {
            throw UndertowMessages.MESSAGES.streamNotRegistered();
        }
        existing.sinkChannel = synResponse;
    }

    void removeStreamSink(int streamId) {
        StreamHolder existing = currentStreams.get(streamId);
        if(existing == null) {
            return;
        }
        existing.sinkClosed = true;
        existing.sinkChannel = null;
        if(existing.sourceClosed) {
            currentStreams.remove(streamId);
        }
        if(isLastFrameReceived() && currentStreams.isEmpty()) {
            sendGoAway(ERROR_NO_ERROR);
        }
    }

    public boolean isClient() {
        return streamIdCounter % 2 == 1;
    }


    HpackEncoder getEncoder() {
        return encoder;
    }

    HpackDecoder getDecoder() {
        return decoder;
    }

    int getPaddingBytes() {
        if(paddingRandom == null) {
            return 0;
        }
        return paddingRandom.nextInt(maxPadding);
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
        if (o == null) {
            return Collections.emptyList();
        }
        return (List) o;
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
                final AttachmentList<T> newList = new AttachmentList<>((Class<T>) Object.class);
                attachments.put(key, newList);
                newList.add(value);
            } else {
                list.add(value);
            }
        }
    }

    public void sendRstStream(int streamId, int statusCode) {
        handleRstStream(streamId);
        Http2RstStreamSinkChannel channel = new Http2RstStreamSinkChannel(this, streamId, statusCode);
        flushChannelIgnoreFailure(channel);
    }

    private void handleRstStream(int streamId) {
        StreamHolder holder = currentStreams.remove(streamId);
        if(holder != null) {
            if (holder.sinkChannel != null) {
                holder.sinkChannel.rstStream();
            }
            if (holder.sourceChannel != null) {
                holder.sourceChannel.rstStream();
            }
        }
    }

    /**
     * Creates a response stream to respond to the initial HTTP upgrade
     *
     * @return
     */
    public Http2HeadersStreamSinkChannel createInitialUpgradeResponseStream() {
        if (lastGoodStreamId != 0) {
            throw new IllegalStateException();
        }
        lastGoodStreamId = 1;
        Http2HeadersStreamSinkChannel stream = new Http2HeadersStreamSinkChannel(this, 1);
        StreamHolder streamHolder = new StreamHolder(stream);
        streamHolder.sourceClosed = true;
        currentStreams.put(1, streamHolder);
        return stream;

    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public boolean isPeerGoneAway() {
        return peerGoneAway;
    }

    public boolean isThisGoneAway() {
        return thisGoneAway;
    }

    Http2StreamSourceChannel removeStreamSource(int streamId) {
        StreamHolder existing = currentStreams.get(streamId);
        if(existing == null){
            return null;
        }
        existing.sourceClosed = true;
        Http2StreamSourceChannel ret = existing.sourceChannel;
        existing.sourceChannel = null;
        if(existing.sinkClosed) {
            currentStreams.remove(streamId);
        }
        return ret;
    }

    Http2StreamSourceChannel getIncomingStream(int streamId) {
        StreamHolder existing = currentStreams.get(streamId);
        if(existing == null){
            return null;
        }
        return existing.sourceChannel;
    }

    private class Http2ControlMessageExceptionHandler implements ChannelExceptionHandler<AbstractHttp2StreamSinkChannel> {
        @Override
        public void handleException(AbstractHttp2StreamSinkChannel channel, IOException exception) {
            IoUtils.safeClose(channel);
            handleBrokenSinkChannel(exception);
        }
    }

    public int getReceiveMaxFrameSize() {
        return receiveMaxFrameSize;
    }

    public int getSendMaxFrameSize() {
        return sendMaxFrameSize;
    }

    public String getProtocol() {
        return protocol;
    }

    private boolean isIdle(int streamNo) {
        if(streamNo % 2 == streamIdCounter % 2) {
            return streamNo >= streamIdCounter;
        } else {
            return streamNo > lastGoodStreamId;
        }
    }

    static final class StreamHolder {
        boolean sourceClosed = false;
        boolean sinkClosed = false;
        Http2StreamSourceChannel sourceChannel;
        Http2StreamSinkChannel sinkChannel;

        StreamHolder(Http2StreamSourceChannel sourceChannel) {
            this.sourceChannel = sourceChannel;
        }

        StreamHolder(Http2StreamSinkChannel sinkChannel) {
            this.sinkChannel = sinkChannel;
        }
    }
}
