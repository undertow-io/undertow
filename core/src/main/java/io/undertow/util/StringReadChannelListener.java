package io.undertow.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

/**
 * Simple utility class for reading a string
 * <p/>
 * todo: handle unicode properly
 *
 * @author Stuart Douglas
 */
public abstract class StringReadChannelListener implements ChannelListener<StreamSourceChannel> {

    private final StringBuilder string = new StringBuilder();
    private final Pool<ByteBuffer> bufferPool;

    public StringReadChannelListener(final Pool<ByteBuffer> bufferPool) {
        this.bufferPool = bufferPool;
    }

    public void setup(final StreamSourceChannel channel) {
        Pooled<ByteBuffer> resource = bufferPool.allocate();
        ByteBuffer buffer = resource.getResource();
        try {
            int r = 0;
            do {
                r = channel.read(buffer);
                if (r == 0) {
                    channel.getReadSetter().set(this);
                    channel.resumeReads();
                } else if (r == -1) {
                    stringDone(string.toString());
                    IoUtils.safeClose(channel);
                } else {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        string.append((char) buffer.get());
                    }
                }
            } while (r > 0);
        } catch (IOException e) {
            error(e);
        } finally {
            resource.free();
        }
    }

    @Override
    public void handleEvent(final StreamSourceChannel channel) {
        Pooled<ByteBuffer> resource = bufferPool.allocate();
        ByteBuffer buffer = resource.getResource();
        try {
            int r = 0;
            do {
                r = channel.read(buffer);
                if (r == 0) {
                    return;
                } else if (r == -1) {
                    stringDone(string.toString());
                    IoUtils.safeClose(channel);
                } else {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        string.append((char) buffer.get());
                    }
                }
            } while (r > 0);
        } catch (IOException e) {
            error(e);
        } finally {
            resource.free();
        }
    }

    protected abstract void stringDone(String string);

    protected abstract void error(IOException e);
}
