package io.undertow.server;

import org.xnio.ChannelListener;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;

/**
 * Represents a buffer that is used when processing pipelined requests, that allows the server to
 * buffer multiple responses into a single write() call.
 *
 * This can improve performance when pipelining requests.
 *
 * @author Stuart Douglas
 */
public interface PipeLiningBuffer {

    /**
     * Flushes the cached data.
     *
     * This should be called when a read thread fails to read any more request data, to make sure that any
     * buffered data is flushed after the last pipelined request.
     *
     * If this returns false the read thread should suspend reads and resume writes
     *
     * @throws IOException
     * @return <code>true</code> If the flush suceeded, false otherwise
     */
    boolean flushPipelinedData() throws IOException;

    /**
     * Gets the channel wrapper that implements the buffering
     *
     * @return The channel wrapper
     */
    ChannelWrapper<StreamSinkChannel> getChannelWrapper();

    /**
     * This method should be called when the underlying channel
     * is upgraded.
     */
    void upgradeUnderlyingChannel();

}
