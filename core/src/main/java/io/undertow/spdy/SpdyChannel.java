package io.undertow.spdy;

import io.undertow.UndertowMessages;
import io.undertow.server.protocol.framed.AbstractFramedChannel;
import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.util.HeaderMap;
import org.xnio.Bits;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
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
public class SpdyChannel extends AbstractFramedChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> {

    static final int DEFAULT_INITIAL_WINDOW_SIZE = 64 * 1024 * 0124;

    static final int SYN_STREAM = 1;
    static final int SYN_REPLY = 2;
    static final int RST_STREAM = 3;
    static final int SETTINGS = 4;
    static final int PING = 6;
    static final int GOAWAY = 7;
    static final int HEADERS = 8;
    static final int WINDOW_UPDATE = 9;

    static final int FLAG_FIN = 1;
    static final int FLAG_UNIDIRECTIONAL = 2;
    static final int CONTROL_FRAME = 1 << 31;

    private final Inflater inflater = new Inflater(false);
    private final Deflater deflater = new Deflater(6);

    private SpdyFrameParser frameParser;
    private final Map<Integer, SpdyStreamSourceChannel> incomingStreams = new ConcurrentHashMap<Integer, SpdyStreamSourceChannel>();
    private final Map<Integer, SpdyStreamStreamSinkChannel> outgoingStreams = new ConcurrentHashMap<Integer, SpdyStreamStreamSinkChannel>();

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

    private int streamIdCounter = 1;

    public SpdyChannel(StreamConnection connectedStreamChannel, Pool<ByteBuffer> bufferPool, Pooled<ByteBuffer> data, Pool<ByteBuffer> heapBufferPool) {
        super(connectedStreamChannel, bufferPool, SpdyFramePriority.INSTANCE, data);
        this.heapBufferPool = heapBufferPool;
        this.deflater.setDictionary(SpdyProtocolUtils.SPDY_DICT);
    }

    @Override
    protected SpdyStreamSourceChannel createChannel(FrameHeaderData frameHeaderData, Pooled<ByteBuffer> frameData) throws IOException {
        SpdyFrameParser frameParser = (SpdyFrameParser) frameHeaderData;
        SpdyStreamSourceChannel channel;
        //note that not all frame types are covered here, as some are only relevant to already active streams
        //if which case they are handled by the existing channel support
        switch (frameParser.type) {
            case SYN_STREAM: {
                SpdySynStreamParser parser = (SpdySynStreamParser) frameParser.parser;
                channel = new SpdySynStreamStreamSourceChannel(this, frameData, frameHeaderData.getFrameLength(), deflater, parser.getHeaderMap(), parser.streamId);
                if (!Bits.anyAreSet(frameParser.flags, FLAG_FIN)) {
                    incomingStreams.put(parser.streamId, channel);
                }
                break;
            }
            case SYN_REPLY: {
                SpdySynReplyParser parser = (SpdySynReplyParser) frameParser.parser;
                channel = new SpdySynReplyStreamSourceChannel(this, frameData, frameHeaderData.getFrameLength(), parser.getHeaderMap(), parser.streamId);
                if (!Bits.anyAreSet(frameParser.flags, FLAG_FIN)) {
                    incomingStreams.put(parser.streamId, channel);
                }
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
        IoUtils.safeClose(this);
    }

    @Override
    protected void handleBrokenSinkChannel(Throwable e) {
        IoUtils.safeClose(this);
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

    public synchronized void handleWindowUpdate(int streamId, int deltaWindowSize) {
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

    synchronized void notifyFlowControlAllowed() {
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
        receiveWindowSize -= read;
        //TODO: make this configurable, we should be able to set the policy that is used to determine when to update the window size
        int initialWindowSize = this.initialWindowSize;
        if (receiveWindowSize < (initialWindowSize / 2)) {
            sendUpdateWindowSize(0, initialWindowSize - receiveWindowSize);
        }
    }

    public synchronized SpdySynStreamStreamSinkChannel createStream(HeaderMap requestHeaders) {
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

    public void registerStreamSink(SpdySynReplyStreamSinkChannel synResponse) {
        outgoingStreams.put(synResponse.getStreamId(), synResponse);
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

        PushBackParser parser = null;

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
                        parser = new SpdySettingsParser(getBufferPool(), length);
                        break;
                    }
                    case WINDOW_UPDATE: {
                        parser = new SpdyWindowUpdateParser(getBufferPool(), length);
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
            handleBrokenSinkChannel(exception);
        }
    }
}
