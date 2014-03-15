package io.undertow.conduits;

import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.StreamSourceConduit;

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
public class DebuggingStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private static final List<byte[]> data = new CopyOnWriteArrayList<byte[]>();

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    public DebuggingStreamSourceConduit(StreamSourceConduit next) {
        super(next);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int pos = dst.position();
        int res = super.read(dst);
        if (res > 0) {
            byte[] d = new byte[res];
            for (int i = 0; i < res; ++i) {
                d[i] = dst.get(i + pos);
            }
            data.add(d);
        }
        return res;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
        for (int i = offs; i < len; ++i) {
            if (dsts[i].hasRemaining()) {
                return read(dsts[i]);
            }
        }
        return 0;
    }

    public static void dump() {

        for (int i = 0; i < data.size(); ++i) {
            System.out.println("Buffer " + i);
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
