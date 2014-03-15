package io.undertow.server.protocol.framed;

import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Source channel, used to receive framed messages.
 *
 * @author Stuart Douglas
 */
public abstract class AbstractFramedStreamSourceChannel<C extends AbstractFramedChannel<C, R, S>, R extends AbstractFramedStreamSourceChannel<C, R, S>, S extends AbstractFramedStreamSinkChannel<C, R, S>> implements StreamSourceChannel {

    private final ChannelListener.SimpleSetter<? extends R> readSetter = new ChannelListener.SimpleSetter();
    private final ChannelListener.SimpleSetter<? extends R> closeSetter = new ChannelListener.SimpleSetter();

    /**
     * The underlying channel. Should not be used directly unless the data
     * buffer is null and {@link #frameDataRemaining} is non-zero.
     */
    private final StreamSourceChannel underlying;
    private final AbstractFramedChannel<C, R, S> framedChannel;
    private final Deque<FrameData> pendingFrameData = new LinkedList<FrameData>();

    private int state = 0;

    private static final int STATE_DONE = 1 << 1;
    private static final int STATE_READS_RESUMED = 1 << 2;
    private static final int STATE_CLOSED = 1 << 3;
    private static final int STATE_LAST_FRAME = 1 << 4;
    private static final int STATE_IN_LISTENER_LOOP = 1 << 5;


    /**
     * The backing data for the current frame.
     */
    private Pooled<ByteBuffer> data;

    /**
     * The amount of data left in the frame. If this is larger than the data in the backing buffer then
     */
    private long frameDataRemaining;

    private final Object lock = new Object();
    private int waiters;
    private volatile boolean waitingForFrame;
    private int readFrameCount = 0;

    public AbstractFramedStreamSourceChannel(AbstractFramedChannel<C, R, S> framedChannel) {
        this.underlying = framedChannel.getSourceChannel();
        this.framedChannel = framedChannel;
        this.waitingForFrame = true;
    }

    public AbstractFramedStreamSourceChannel(AbstractFramedChannel<C, R, S> framedChannel, Pooled<ByteBuffer> data, long frameDataRemaining) {
        this.underlying = framedChannel.getSourceChannel();
        this.framedChannel = framedChannel;
        this.waitingForFrame = data == null && frameDataRemaining <= 0;
        this.data = data;
        this.frameDataRemaining = frameDataRemaining;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        if (anyAreSet(state, STATE_DONE)) {
            return -1;
        }
        beforeRead();
        if (waitingForFrame) {
            return 0;
        }
        try {
            if (frameDataRemaining == 0 && anyAreSet(state, STATE_LAST_FRAME)) {
                return -1;
            } else if (data != null) {
                int old = data.getResource().limit();
                try {
                    if (count < data.getResource().remaining()) {
                        data.getResource().limit((int) (data.getResource().position() + count));
                    }
                    int written = target.write(data.getResource(), position);
                    frameDataRemaining -= written;
                    return written;
                } finally {
                    data.getResource().limit(old);
                }
            } else if (frameDataRemaining > 0) {
                long toTransfer = count;
                if (toTransfer > frameDataRemaining) {
                    toTransfer = frameDataRemaining;
                }
                long written = underlying.transferTo(position, toTransfer, target);
                frameDataRemaining -= written;
                return written;
            }
            return 0;
        } finally {
            exitRead();
        }
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel streamSinkChannel) throws IOException {
        if (anyAreSet(state, STATE_DONE)) {
            return -1;
        }
        beforeRead();
        if (waitingForFrame) {
            return 0;
        }
        try {
            if (frameDataRemaining == 0 && anyAreSet(state, STATE_LAST_FRAME)) {
                return -1;
            } else if (data != null && data.getResource().hasRemaining()) {
                int old = data.getResource().limit();
                try {
                    throughBuffer.position(throughBuffer.limit());
                    if (count < data.getResource().remaining()) {
                        data.getResource().limit((int) (data.getResource().position() + count));
                    }
                    int written = streamSinkChannel.write(data.getResource());
                    frameDataRemaining -= written;
                    return written;
                } finally {
                    data.getResource().limit(old);
                }
            } else if (frameDataRemaining > 0) {
                long toTransfer = count;
                if (toTransfer > frameDataRemaining) {
                    toTransfer = frameDataRemaining;
                }
                long written = underlying.transferTo(toTransfer, throughBuffer, streamSinkChannel);
                frameDataRemaining -= written;
                return written;
            } else {
                throughBuffer.position(throughBuffer.limit());
            }
            return 0;
        } finally {
            exitRead();
        }
    }

    @Override
    public void suspendReads() {
        state &= ~STATE_READS_RESUMED;
    }

    /**
     * Method that is invoked when all data has been read.
     *
     * @throws IOException
     */
    protected void complete() throws IOException {

    }

    protected boolean isComplete() {
        return anyAreSet(state, STATE_DONE);
    }

    @Override
    public void resumeReads() {
        resumeReads(false);
    }

    @Override
    public boolean isReadResumed() {
        return anyAreSet(state, STATE_READS_RESUMED);
    }

    @Override
    public void wakeupReads() {
        resumeReads(true);
    }

    void resumeReads(final boolean wakeup) {
        state |= STATE_READS_RESUMED;
        if (data == null && frameDataRemaining > 0) {
            if (wakeup) {
                underlying.wakeupReads();
            } else {
                underlying.resumeReads();
            }
        } else {
            if (!anyAreSet(state, STATE_IN_LISTENER_LOOP)) {
                getIoThread().execute(new Runnable() {

                    @Override
                    public void run() {
                        state |= STATE_IN_LISTENER_LOOP;
                        try {
                            do {
                                ChannelListener<? super R> listener = getReadListener();
                                if (listener == null || !isReadResumed()) {
                                    return;
                                }
                                ChannelListeners.invokeChannelListener((R) AbstractFramedStreamSourceChannel.this, listener);
                                //if writes are shutdown or we become active then we stop looping
                                //we stop when writes are shutdown because we can't flush until we are active
                                //although we may be flushed as part of a batch
                            } while (allAreClear(state, STATE_CLOSED) && frameDataRemaining > 0);
                        } finally {
                            state &= ~STATE_IN_LISTENER_LOOP;
                        }
                    }
                });
            }
        }
    }

    private ChannelListener<? super R> getReadListener() {
        return (ChannelListener<? super R>) readSetter.get();
    }

    @Override
    public void shutdownReads() throws IOException {
        close();
    }

    protected void lastFrame() {
        state |= STATE_LAST_FRAME;
    }

    @Override
    public void awaitReadable() throws IOException {
        if (data == null) {
            if (frameDataRemaining > 0) {
                underlying.awaitReadable();
            } else {
                synchronized (lock) {
                    if (data == null) {
                        try {
                            waiters++;
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException();
                        } finally {
                            waiters--;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void awaitReadable(long l, TimeUnit timeUnit) throws IOException {
        if (data == null) {
            if (frameDataRemaining > 0) {
                underlying.awaitReadable(l, timeUnit);
            } else {
                synchronized (lock) {
                    if (data == null) {
                        try {
                            waiters++;
                            lock.wait(timeUnit.toMillis(l));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException();
                        } finally {
                            waiters--;
                        }
                    }
                }
            }
        }
    }

    void dataReady(FrameHeaderData headerData, Pooled<ByteBuffer> frameData) {
        synchronized (lock) {
            if (this.frameDataRemaining == 0 && pendingFrameData.isEmpty()) {
                if(frameData.getResource().hasRemaining()) {
                    this.data = frameData;
                } else {
                    frameData.free();
                }
                this.frameDataRemaining = headerData.getFrameLength();
                if (waiters > 0) {
                    lock.notifyAll();
                }
                handleHeaderData(headerData);
                if (anyAreSet(state, STATE_READS_RESUMED)) {
                    resumeReads(false);
                }
                waitingForFrame = false;
            } else {
                this.pendingFrameData.add(new FrameData(headerData, frameData));
            }
        }
    }

    protected void handleHeaderData(FrameHeaderData headerData) {

    }

    @Override
    public XnioExecutor getReadThread() {
        return underlying.getIoThread();
    }

    @Override
    public ChannelListener.Setter<? extends R> getReadSetter() {
        return readSetter;
    }

    @Override
    public ChannelListener.Setter<? extends R> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return underlying.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return underlying.getIoThread();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return false;
    }

    @Override
    public <T> T getOption(Option<T> tOption) throws IOException {
        return null;
    }

    @Override
    public <T> T setOption(Option<T> tOption, T t) throws IllegalArgumentException, IOException {
        return null;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (anyAreSet(state, STATE_DONE)) {
            return -1;
        }
        beforeRead();
        if (waitingForFrame) {
            return 0;
        }
        try {
            if (frameDataRemaining == 0 && anyAreSet(state, STATE_LAST_FRAME)) {
                return -1;
            } else if (data != null) {
                int old = data.getResource().limit();
                try {
                    long count = Buffers.remaining(dsts, offset, length);
                    if (count < data.getResource().remaining()) {
                        data.getResource().limit((int) (data.getResource().position() + count));
                    } else {
                        count = data.getResource().remaining();
                    }
                    int written = Buffers.copy((int) count, dsts, offset, length, data.getResource());
                    frameDataRemaining -= written;
                    return written;
                } finally {
                    data.getResource().limit(old);
                }
            } else if (frameDataRemaining > 0) {
                long toTransfer = Buffers.remaining(dsts, offset, length);
                if (toTransfer > frameDataRemaining) {
                    toTransfer = frameDataRemaining;
                }
                int lim;
                // The total amount of buffer space discovered so far.
                long t = 0L;
                for (int i = 0; i < length; i++) {
                    final ByteBuffer buffer = dsts[i + offset];
                    // Grow the discovered buffer space by the remaining size of the current buffer.
                    // We want to capture the limit so we calculate "remaining" ourselves.
                    t += (lim = buffer.limit()) - buffer.position();
                    if (t > toTransfer) {
                        // only read up to this point, and trim the last buffer by the number of extra bytes
                        buffer.limit(lim - (int) (t - toTransfer));
                        try {
                            long read = underlying.read(dsts, offset, i + 1);
                            frameDataRemaining -= read;
                            return read;
                        } finally {
                            // restore the original limit
                            buffer.limit(lim);
                        }
                    }
                }
                long read = underlying.read(dsts, offset, length);
                frameDataRemaining -= read;
                return read;
            }
            return 0;
        } finally {
            exitRead();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (anyAreSet(state, STATE_DONE)) {
            return -1;
        }
        beforeRead();
        if (waitingForFrame) {
            return 0;
        }
        try {
            if (frameDataRemaining == 0 && anyAreSet(state, STATE_LAST_FRAME)) {
                return -1;
            } else if (data != null) {
                int old = data.getResource().limit();
                try {
                    int count = dst.remaining();
                    if (count < data.getResource().remaining()) {
                        data.getResource().limit(data.getResource().position() + count);
                    } else {
                        count = data.getResource().remaining();
                    }
                    int written = Buffers.copy(count, dst, data.getResource());
                    frameDataRemaining -= written;
                    return written;
                } finally {
                    data.getResource().limit(old);
                }
            } else if (frameDataRemaining > 0) {
                int old = dst.limit();
                try {
                    if (dst.remaining() > frameDataRemaining) {
                        dst.limit((int) (dst.position() + frameDataRemaining));
                    }
                    int written = underlying.read(dst);
                    frameDataRemaining -= written;
                    return written;
                } finally {
                    dst.limit(old);
                }
            }
            return 0;
        } finally {
            exitRead();
        }
    }

    private void beforeRead() {
        if (frameDataRemaining == 0) {
            synchronized (lock) {
                FrameData pending = pendingFrameData.poll();
                if (pending != null) {
                    this.data = pending.getFrameData();
                    this.frameDataRemaining = pending.getFrameHeaderData().getFrameLength();
                    handleHeaderData(pending.getFrameHeaderData());
                }
            }
        }
    }

    private void exitRead() throws IOException {
        if (data != null && !data.getResource().hasRemaining()) {
            data.free();
            data = null;
        }
        if (frameDataRemaining == 0) {
            synchronized (lock) {
                readFrameCount++;
                if (pendingFrameData.isEmpty()) {
                    try {
                        if (anyAreSet(state, STATE_LAST_FRAME)) {
                            state |= STATE_DONE;
                            complete();
                        } else {
                            waitingForFrame = true;
                        }
                    } finally {
                        framedChannel.notifyFrameReadComplete(this);
                    }
                }
            }
        }
    }

    @Override
    public boolean isOpen() {
        return allAreClear(state, STATE_CLOSED);
    }

    @Override
    public void close() throws IOException {
        state |= STATE_CLOSED;
        if (allAreClear(state, STATE_DONE)) {
            framedChannel.markReadsBroken(null);
        }
    }

    protected AbstractFramedChannel<C, R, S> getFramedChannel() {
        return framedChannel;
    }

    protected int getReadFrameCount() {
        return readFrameCount;
    }

    private class FrameData {

        private final FrameHeaderData frameHeaderData;
        private final Pooled<ByteBuffer> frameData;

        FrameData(FrameHeaderData frameHeaderData, Pooled<ByteBuffer> frameData) {
            this.frameHeaderData = frameHeaderData;
            this.frameData = frameData;
        }

        FrameHeaderData getFrameHeaderData() {
            return frameHeaderData;
        }

        Pooled<ByteBuffer> getFrameData() {
            return frameData;
        }
    }

}
