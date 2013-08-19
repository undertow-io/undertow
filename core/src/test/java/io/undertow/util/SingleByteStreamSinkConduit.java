package io.undertow.util;

import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A channel that will only write a single byte at a time for a set number of calls to write.
 *
 * This can be used for testing purposes, to make sure that resuming writes works as expected.
 *
 * @author Stuart Douglas
 */
public class SingleByteStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final int singleByteWrites;

    private int state = 0;

    /**
     * Construct a new instance.
     *
     * @param next             the delegate conduit to set
     * @param singleByteWrites
     */
    public SingleByteStreamSinkConduit(StreamSinkConduit next, int singleByteWrites) {
        super(next);
        this.singleByteWrites = singleByteWrites;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (state > singleByteWrites) {
            return next.write(src);
        }
        if (state++ % 2 == 0) {
            return 0;
        } else {
            if (src.remaining() == 0) {
                return 0;
            }
            int limit = src.limit();
            try {
                src.limit(src.position() + 1);
                return next.write(src);
            } finally {
                src.limit(limit);
            }
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        if (state > singleByteWrites) {
            return next.write(srcs, offs, len);
        }
        if (state++ % 2 == 0) {
            return 0;
        } else {
            ByteBuffer src = null;
            for(int i = offs; i < offs + len; ++i) {
                if(srcs[i].hasRemaining()) {
                    src = srcs[i];
                    break;
                }
            }
            if(src == null) {
                return 0;
            }
            int limit = src.limit();
            try {
                src.limit(src.position() + 1);
                return next.write(src);
            } finally {
                src.limit(limit);
            }
        }
    }
}
