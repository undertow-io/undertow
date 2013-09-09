package io.undertow.util;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public class SingleByteStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private final int singleByteReads;

    private int state = 0;


    /**
     * Construct a new instance.
     *
     * @param next            the delegate conduit to set
     * @param singleByteReads
     */
    public SingleByteStreamSourceConduit(StreamSourceConduit next, int singleByteReads) {
        super(next);
        this.singleByteReads = singleByteReads;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (state > singleByteReads) {
            return next.read(dst);
        }

        if (state++ % 2 == 0) {
            return 0;
        } else {
            if (dst.remaining() == 0) {
                return 0;
            }
            int limit = dst.limit();
            try {
                dst.limit(dst.position() + 1);
                return next.read(dst);
            } finally {
                dst.limit(limit);
            }
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
        if (state > singleByteReads) {
            return next.read(dsts, offs, len);
        }
        if (state++ % 2 == 0) {
            return 0;
        } else {
            ByteBuffer dst = null;
            for (int i = offs; i < offs + len; ++i) {
                if (dsts[i].hasRemaining()) {
                    dst = dsts[i];
                    break;
                }
            }
            if (dst == null) {
                return 0;
            }
            int limit = dst.limit();
            try {
                dst.limit(dst.position() + 1);
                return next.read(dst);
            } finally {
                dst.limit(limit);
            }
        }
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        if (state > singleByteReads) {
            return next.transferTo(position, count, target);
        }
        if (state++ % 2 == 0) {
            return 0;
        } else {
            return next.transferTo(position, count == 0 ? 0 : count, target);
        }
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        if (state > singleByteReads) {
            return next.transferTo(count, throughBuffer, target);
        }
        if (state++ % 2 == 0) {
            throughBuffer.position(throughBuffer.limit());
            return 0;
        } else {
            return next.transferTo(count == 0 ? 0 : count, throughBuffer, target);
        }
    }
}
