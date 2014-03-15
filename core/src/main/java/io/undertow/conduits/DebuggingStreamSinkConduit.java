package io.undertow.conduits;

import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Conduit that saves all the data that is written through it and can dump it to the console
 * <p/>
 * Obviously this should not be used in production.
 *
 * @author Stuart Douglas
 */
public class DebuggingStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private static final List<byte[]> data = new CopyOnWriteArrayList<byte[]>();

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    public DebuggingStreamSinkConduit(StreamSinkConduit next) {
        super(next);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int pos = src.position();
        int res = super.write(src);
        if (res > 0) {
            byte[] d = new byte[res];
            for (int i = 0; i < res; ++i) {
                d[i] = src.get(i + pos);
            }
            data.add(d);
        }
        return res;
    }

    @Override
    public long write(ByteBuffer[] dsts, int offs, int len) throws IOException {
        for (int i = offs; i < len; ++i) {
            if (dsts[i].hasRemaining()) {
                return write(dsts[i]);
            }
        }
        return 0;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    public static void dump() {

        for (int i = 0; i < data.size(); ++i) {
            System.out.println("Write Buffer " + i);
            StringBuilder sb = new StringBuilder();
            try {
                Buffers.dump(ByteBuffer.wrap(data.get(i)), sb, 0, 20);
            } catch (IOException e) {
                new RuntimeException(e);
            }
            System.out.println(sb);
            System.out.println();
        }

    }
}
