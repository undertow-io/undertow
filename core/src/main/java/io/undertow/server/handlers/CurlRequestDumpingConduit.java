package io.undertow.server.handlers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.StreamSourceConduit;

public class CurlRequestDumpingConduit extends AbstractStreamSourceConduit<StreamSourceConduit>
{

    private final List<byte[]> data = new CopyOnWriteArrayList<>();

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    public CurlRequestDumpingConduit(StreamSourceConduit next)
    {
        super(next);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException
    {
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target)
            throws IOException
    {
        return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException
    {
        int pos = dst.position();
        int res = super.read(dst);
        if (res > 0)
        {
            byte[] d = new byte[res];
            for(int i = 0; i < res; ++i)
            {
                d[i] = dst.get(i + pos);
            }
            data.add(d);
        }
        return res;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException
    {
        for(int i = offs; i < len; ++i)
        {
            if (dsts[i].hasRemaining())
            {
                return read(dsts[i]);
            }
        }
        return 0;
    }

    public String fullRequestDump()
    {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < data.size(); ++i)
        {
            sb.append(new String(data.get(i)));
        }
        return sb.toString();
    }
}
