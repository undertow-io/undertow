package io.undertow.spdy;

import io.undertow.UndertowMessages;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
 abstract class SpdyControlFrameStreamSinkChannel extends SpdyStreamSinkChannel {

    protected SpdyControlFrameStreamSinkChannel(SpdyChannel channel) {
        super(channel);
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        throw UndertowMessages.MESSAGES.controlFrameCannotHaveBodyContent();
    }
}
