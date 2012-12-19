package io.undertow.ajp;

import io.undertow.channels.DelegatingStreamSourceChannel;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

/**
 * Underlying AJP request channel.
 *
 * @author Stuart Douglas
 */
public class AjpRequestChannel extends DelegatingStreamSourceChannel<AjpRequestChannel> {

    private static final ByteBuffer READ_BODY_CHUNK;

    static {
        ByteBuffer readBody = ByteBuffer.allocateDirect(7);
        readBody.put((byte) 'A');
        readBody.put((byte) 'B');
        readBody.put((byte) 0);
        readBody.put((byte) 3);
        readBody.put((byte) 6);
        readBody.put((byte) 0x1F);
        readBody.put((byte) 0xFA);
        readBody.flip();
        READ_BODY_CHUNK = readBody;

    }

    private final AjpResponseChannel ajpResponseChannel;

    /**
     * The size of the incoming request. A size of 0 indicates that the request is using chunked encoding
     */
    private final Long size;

    private static final int HEADER_LENGTH = 6;

    /**
     * byte buffer that is used to hold header data
     */
    private final ByteBuffer headerBuffer = ByteBuffer.allocateDirect(HEADER_LENGTH);


    /**
     * The total amount of remaining data. If this is unknown it is -1.
     */
    private long remaining;

    /**
     * State flags, with the chunk remaining stored in the low bytes
     */
    private long state;

    /**
     * There is a packet coming from apache.
     */
    private static final long STATE_READING = 1L << 63L;
    /**
     * There is no packet coming, as we need to set a GET_BODY_CHUNK message
     */
    private static final long STATE_SEND_REQUIRED = 1L << 62L;
    /**
     * read is done
     */
    private static final long STATE_FINISHED = 1L << 61L;

    /**
     * The remaining bits are used to store the remaining chunk size.
     */
    private static final long STATE_MASK = longBitMask(0, 60);

    public AjpRequestChannel(final StreamSourceChannel delegate, AjpResponseChannel ajpResponseChannel, Long size) {
        super(delegate);
        this.ajpResponseChannel = ajpResponseChannel;
        this.size = size;
        if (size == null) {
            state = STATE_SEND_REQUIRED;
            remaining = -1;
        } else if (size == 0) {
            state = STATE_FINISHED;
            remaining = 0;
        } else {
            state = STATE_READING;
            remaining = size;
        }
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        return target.transferFrom(this, position, count);
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(this, count, throughBuffer, target);
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < length; ++i) {
            while (dsts[i].hasRemaining()) {
                int r = read(dsts[i]);
                if (r <= 0 && total > 0) {
                    return total;
                } else if (r <= 0) {
                    return r;
                } else {
                    total += r;
                }
            }
        }
        return total;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        long state = this.state;
        if(anyAreSet(state, STATE_FINISHED)) {
            return -1;
        } else if (anyAreSet(state, STATE_SEND_REQUIRED)) {
            state = this.state = (state & STATE_MASK) | STATE_READING;
            if (!ajpResponseChannel.doGetRequestBodyChunk(READ_BODY_CHUNK.duplicate(), this)) {
                return 0;
            }
        }
        //we might have gone into state_reading above
        if (anyAreSet(state, STATE_READING)) {
            return doRead(dst, state);
        }
        assert STATE_FINISHED == state;
        return -1;
    }

    private int doRead(final ByteBuffer dst, long state) throws IOException {
        ByteBuffer headerBuffer = this.headerBuffer;
        long headerRead = HEADER_LENGTH - headerBuffer.remaining();
        long remaining = this.remaining;
        if (remaining == 0) {
            this.state = STATE_FINISHED;
            return -1;
        }
        long chunkRemaining;
        if (headerRead != HEADER_LENGTH) {
            int read = delegate.read(headerBuffer);
            if (read == -1) {
                return read;
            } else if (headerBuffer.hasRemaining()) {
                return 0;
            } else {
                headerBuffer.flip();
                byte b1 = headerBuffer.get(); //0x12
                byte b2 = headerBuffer.get(); //0x34
                assert b1 == 0x12;
                assert b2 == 0x34;
                headerBuffer.get();//the length headers, two less than the string length header
                headerBuffer.get();
                b1 = headerBuffer.get();
                b2 = headerBuffer.get();
                chunkRemaining = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                if(chunkRemaining == 0) {
                    this.remaining = 0;
                    this.state = STATE_FINISHED;
                    return -1;
                }
            }
        } else {
            chunkRemaining = this.state & ~STATE_MASK;
        }

        int limit = dst.limit();
        try {
            if (limit > chunkRemaining) {
                dst.limit((int) (dst.position() + chunkRemaining));
            }
            int read = delegate.read(dst);
            chunkRemaining -= read;
            if(remaining != -1) {
                remaining -= read;
            }
            if (remaining == 0) {
                this.state = STATE_FINISHED;
            } else if (chunkRemaining == 0) {
                headerBuffer.clear();
                this.state = STATE_SEND_REQUIRED;
            } else {
                this.state = (state & ~STATE_MASK) | chunkRemaining;
            }
            return read;
        } finally {
            this.remaining = remaining;
            dst.limit(limit);
        }
    }

    @Override
    public void awaitReadable() throws IOException {
        if (anyAreSet(state, STATE_READING)) {
            delegate.awaitReadable();
        }
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        if (anyAreSet(state, STATE_READING)) {
            delegate.awaitReadable(time, timeUnit);
        }
    }

}
