package io.undertow.server.protocol.framed;

import org.xnio.Pooled;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class SendFrameHeader {

    private final int reminingInBuffer;
    private final Pooled<ByteBuffer> byteBuffer;

    public SendFrameHeader(int reminingInBuffer, Pooled<ByteBuffer> byteBuffer) {
        this.byteBuffer = byteBuffer;
        this.reminingInBuffer = reminingInBuffer;
    }

    public SendFrameHeader(Pooled<ByteBuffer> byteBuffer) {
        this.byteBuffer = byteBuffer;
        this.reminingInBuffer = 0;
    }

    public Pooled<ByteBuffer> getByteBuffer() {
        return byteBuffer;
    }

    public int getReminingInBuffer() {
        return reminingInBuffer;
    }
}
