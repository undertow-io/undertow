package io.undertow.websockets.version00;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;

public class WebSocket00BinaryFrameSourceChannel extends StreamSourceFrameChannel {

    private final int payloadSize;
    private int readBytes;
    public WebSocket00BinaryFrameSourceChannel(StreamSourceChannel channel, WebSocketChannel wsChannel, int payloadSize) {
        super(channel, wsChannel, WebSocketFrameType.BINARY);
        this.payloadSize = payloadSize;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (byteToRead() < dst.remaining()) {
            dst.limit(dst.position() + byteToRead());
        }
        return channel.read(dst);
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        int toRead = byteToRead();
        int l = 0;
        for (int i = offset; i < length; i++) {
            l++;
            ByteBuffer buf = dsts[i];
            int remain = buf.remaining();
            if (remain > toRead) {
                buf.limit(toRead);
                if (l == 0) {
                    int b = channel.read(buf);
                    readBytes += b;
                    return b;
                } else {
                    ByteBuffer[] dstsNew = new ByteBuffer[l];
                    System.arraycopy(dsts, offset, dstsNew, 0, dstsNew.length);
                    long b = channel.read(dstsNew);
                    readBytes += b;
                    return b;
                }
            }
        }
        long b = channel.read(dsts);
        readBytes += b;
        return b;
    }

    private int byteToRead() {
       return payloadSize - readBytes;
    }
}
