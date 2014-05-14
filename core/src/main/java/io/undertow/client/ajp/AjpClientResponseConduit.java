package io.undertow.client.ajp;

import io.undertow.client.UndertowClientMessages;
import io.undertow.conduits.ConduitListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

/**
 * Underlying AJP request channel.
 *
 * @author Stuart Douglas
 */
class AjpClientResponseConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private final AjpClientConnection connection;
    private final AjpClientRequestConduit ajpClientRequestConduit;

    private static final int HEADER_LENGTH = 7;

    private static final int AJP13_SEND_BODY_CHUNK = 3;
    private static final int AJP13_END_RESPONSE = 5;
    private static final int AJP13_GET_BODY_CHUNK = 6;


    /**
     * byte buffer that is used to hold header data
     */
    private final ByteBuffer headerBuffer = ByteBuffer.allocateDirect(HEADER_LENGTH);

    private final ConduitListener<? super AjpClientResponseConduit> finishListener;

    /**
     * State flags, with the chunk remaining stored in the low bytes
     */
    private long state;

    /**
     * read is done
     */
    private static final long STATE_FINISHED = 1L << 63L;

    /**
     * The remaining bits are used to store the remaining chunk size.
     */
    private static final long STATE_MASK = longBitMask(0, 62);

    public AjpClientResponseConduit(final StreamSourceConduit delegate, AjpClientConnection connection, AjpClientRequestConduit ajpClientRequestConduit, ConduitListener<? super AjpClientResponseConduit> finishListener) {
        super(delegate);
        this.connection = connection;
        this.ajpClientRequestConduit = ajpClientRequestConduit;
        this.finishListener = finishListener;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
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
        if (anyAreSet(state, STATE_FINISHED)) {
            return -1;
        }
        return doRead(dst);
    }

    private int doRead(final ByteBuffer dst) throws IOException {
        ByteBuffer headerBuffer = this.headerBuffer;
        boolean val = false;
        long chunkRemaining;
        //most chunks have a header size of 6
        //but AJP13_SEND_BODY_CHUNK has a size of 7
        if (!headerRead()) {
            val = true;
            int read = next.read(headerBuffer);
            if (read == -1) {
                handleFinish();
                return -1;
            } else if (!headerRead()) {
                return 0;
            } else {
                headerBuffer.flip();
                byte b1 = headerBuffer.get(); //A
                byte b2 = headerBuffer.get(); //B
                if(b1 != 'A' || b2 != 'B') {
                    throw UndertowClientMessages.MESSAGES.wrongMagicNumber("AB", "" + ((char)b1) + ((char)b2));
                }
                headerBuffer.get(); //the length headers, two less than the string length header
                headerBuffer.get();

                byte packetType = headerBuffer.get();
                switch (packetType) {
                    case AJP13_GET_BODY_CHUNK: {
                        b1 = headerBuffer.get();
                        b2 = headerBuffer.get();
                        int requestedSize = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                        ajpClientRequestConduit.setBodyChunkRequested(requestedSize);
                        headerBuffer.clear();
                        return 0;
                    }
                    case AJP13_END_RESPONSE: {
                        ajpClientRequestConduit.setRequestDone();
                        byte persistent = headerBuffer.get();
                        if (persistent == 0) {
                            connection.requestClose();
                        }
                        handleFinish();
                        return -1;
                    }
                    case AJP13_SEND_BODY_CHUNK: {
                        b1 = headerBuffer.get();
                        b2 = headerBuffer.get();
                        chunkRemaining = (((b1 & 0xFF) << 8) | (b2 & 0xFF)) + 1; //+1 for the null terminator
                        break;
                    }
                    default: {
                        throw UndertowClientMessages.MESSAGES.unknownAjpMessageType(packetType);
                    }
                }
            }
        } else {
            chunkRemaining = this.state & STATE_MASK;
        }
        if(chunkRemaining <= 0) {
            terminateReads();
            throw new RuntimeException("error " + chunkRemaining + " FLAG: " + val);
        }

        int limit = dst.limit();
        try {
            if (dst.remaining() > chunkRemaining) {
                dst.limit((int) (dst.position() + chunkRemaining));
            }
            int read = next.read(dst);
            chunkRemaining -= read;
            if (chunkRemaining == 0) {
                read--;
                dst.position(dst.position() - 1); //null terminator
                headerBuffer.clear();
            }
            this.state = (state & ~STATE_MASK) | chunkRemaining;
            return read;
        } finally {
            dst.limit(limit);
        }
    }

    private void handleFinish() {
        if (allAreClear(state, STATE_FINISHED)) {
            state |= STATE_FINISHED;
            finishListener.handleEvent(this);
            ajpClientRequestConduit.setRequestDone();
        }
    }

    private boolean headerRead() {
        boolean headerRead = false;
        if (headerBuffer.remaining() == 0) {
            headerRead = true;
        } else if (headerBuffer.remaining() == 1) {
            if (headerBuffer.get(4) != AJP13_SEND_BODY_CHUNK) {
                headerRead = true;
            }
        }
        return headerRead;
    }

    @Override
    public void awaitReadable() throws IOException {
        next.awaitReadable();
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        next.awaitReadable(time, timeUnit);
    }
}
