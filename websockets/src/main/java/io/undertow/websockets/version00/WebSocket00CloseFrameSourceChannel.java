package io.undertow.websockets.version00;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;

public class WebSocket00CloseFrameSourceChannel extends StreamSourceFrameChannel {

    public WebSocket00CloseFrameSourceChannel(StreamSourceChannel channel, WebSocketChannel wsChannel) {
        super(channel, wsChannel);
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        return -1;
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return -1;
    }

    @Override
    public int read(ByteBuffer arg0) throws IOException {
        return -1;
    }

    @Override
    public long read(ByteBuffer[] arg0) throws IOException {
        return -1;
    }

    @Override
    public long read(ByteBuffer[] arg0, int arg1, int arg2) throws IOException {
        return -1;
    }

}
