package io.undertow.server;

import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.SuspendableReadChannel;
import org.xnio.channels.SuspendableWriteChannel;
import org.xnio.channels.WriteTimeoutException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;

/**
 * This class provides utility functionality for blocking operations on {@link java.nio.channels.Channel channels}.
 *
 * @author Carter Kozak
 */
public final class BlockingChannels {

    /**
     * TODO(ckozak): Delete in favor of XNIO Channels.flushBlocking once XNIO-357 has been released.
     *
     * @see <a href="https://github.com/xnio/xnio/pull/213">xnio#214</a>
     * @see <a href="https://issues.redhat.com/browse/XNIO-357">XNIO-357</a>
     */
    private static boolean flushBlocking(
            SuspendableWriteChannel channel,
            long time,
            TimeUnit unit) throws IOException {
        // In the fast path, the timeout is not used because bytes can be flushed without blocking.
        if (channel.flush()) {
            return true;
        }
        long remaining = unit.toNanos(time);
        long now = System.nanoTime();
        do {
            // awaitWritable may return spuriously so looping is required.
            channel.awaitWritable(remaining, TimeUnit.NANOSECONDS);
            // flush prior to recalculating remaining time to avoid a nanoTime
            // invocation in the optimal path.
            if (channel.flush()) {
                return true;
            }
            // Nanotime must only be used in comparison with another nanotime value
            // This implementation allows us to avoid immediate subsequent nanoTime calls
        } while ((remaining -= Math.max(-now + (now = System.nanoTime()), 0L)) > 0L);
        return false;
    }

    /**
     * Same as flushBlocking with two exceptions: A timeout exception is thrown if an operation does not succeed
     * within the time limit, and non-positive values do not apply a timeout.
     * When the timeout is exceeded and an exception is thrown, the provided channel is closed.
     * @throws WriteTimeoutException when a flush does not succeed before the timeout is exceeded.
     */
    public static void flushBlockingOrThrow(
            SuspendableWriteChannel channel,
            long time,
            TimeUnit unit) throws IOException {
        if (time > 0) {
            if (!flushBlocking(channel, time, unit)) {
                IoUtils.safeClose(channel);
                throw new WriteTimeoutException();
            }
        } else {
            Channels.flushBlocking(channel);
        }
    }

    /**
     * TODO(ckozak): Delete in favor of XNIO Channels.readBlocking once XNIO-356 has been released.
     *
     * @see <a href="https://github.com/xnio/xnio/pull/212">xnio#212</a>
     * @see <a href="https://issues.redhat.com/browse/XNIO-356">XNIO-356</a>
     */
    private static <C extends ReadableByteChannel & SuspendableReadChannel> int readBlocking(
            C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        // In the fast path, the timeout is not used because bytes can be read without blocking.
        int res = channel.read(buffer);
        if (res != 0) {
            return res;
        }
        long remaining = unit.toNanos(time);
        long now = System.nanoTime();
        while (buffer.hasRemaining() && remaining > 0) {
            // awaitReadable may return spuriously, so looping is required.
            channel.awaitReadable(remaining, TimeUnit.NANOSECONDS);
            // read prior to recalculating remaining time to avoid a nanoTime
            // invocation in the optimal path.
            res = channel.read(buffer);
            if (res != 0) {
                return res;
            }
            // Nanotime must only be used in comparison with another nanotime value
            // This implementation allows us to avoid immediate subsequent nanoTime calls
            remaining -= Math.max(-now + (now = System.nanoTime()), 0L);
        }
        return res;
    }

    /**
     * Same as readBlocking with two exceptions: A timeout exception is thrown if an operation does not succeed
     * within the time limit, and non-positive values do not apply a timeout.
     * When the timeout is exceeded and an exception is thrown, the provided channel is closed.
     * @throws ReadTimeoutException when a read does not succeed before the timeout is exceeded.
     */
    public static <C extends ReadableByteChannel & SuspendableReadChannel> int readBlockingOrThrow(
            C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        if (time > 0) {
            int result = readBlocking(channel, buffer, time, unit);
            if (result == 0 && buffer.hasRemaining()) {
                IoUtils.safeClose(channel);
                throw new ReadTimeoutException();
            }
            return result;
        } else {
            return Channels.readBlocking(channel, buffer);
        }
    }

    /**
     * TODO(ckozak): Delete in favor of XNIO Channels.writeBlocking once XNIO-356 has been released.
     *
     * @see <a href="https://github.com/xnio/xnio/pull/212">xnio#212</a>
     * @see <a href="https://issues.redhat.com/browse/XNIO-356">XNIO-356</a>
     */
    private static <C extends GatheringByteChannel & SuspendableWriteChannel> long writeBlocking(
            C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toNanos(time);
        long now = System.nanoTime();
        long t = 0;
        while (Buffers.hasRemaining(buffers, offs, len) && remaining > 0L) {
            long res = channel.write(buffers, offs, len);
            if (res == 0) {
                channel.awaitWritable(remaining, TimeUnit.NANOSECONDS);
                remaining -= Math.max(-now + (now = System.nanoTime()), 0L);
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * TODO(ckozak): Delete in favor of XNIO Channels.writeBlocking once XNIO-356 has been released.
     *
     * @see <a href="https://github.com/xnio/xnio/pull/212">xnio#212</a>
     * @see <a href="https://issues.redhat.com/browse/XNIO-356">XNIO-356</a>
     */
    private static <C extends WritableByteChannel & SuspendableWriteChannel> int writeBlocking(
            C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toNanos(time);
        long now = System.nanoTime();
        int t = 0;
        while (buffer.hasRemaining() && remaining > 0L) {
            int res = channel.write(buffer);
            if (res == 0) {
                channel.awaitWritable(remaining, TimeUnit.NANOSECONDS);
                remaining -= Math.max(-now + (now = System.nanoTime()), 0L);
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * Same as writeBlocking with two exceptions: A timeout exception is thrown if a write does not succeed
     * within the time limit, and non-positive values do not apply a timeout.
     * Note that the timeout applies between writes, execution may take longer as long as writes
     * succeed within the specified time.
     * When the timeout is exceeded and an exception is thrown, the provided channel is closed.
     * @throws WriteTimeoutException when a write does not succeed before the timeout is exceeded.
     */
    public static <C extends GatheringByteChannel & SuspendableWriteChannel> long writeBlockingOrThrow(
            C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        if (time > 0) {
            long total = 0;
            // Attempt to write until there is no remaining data, or failure.
            while (Buffers.hasRemaining(buffers, offs, len)) {
                long result = writeBlocking(channel, buffers, offs, len, time, unit);
                if (result == 0 && Buffers.hasRemaining(buffers, offs, len)) {
                    // readBlocking returns zero if either the input does not have any available space
                    // or the timeout was reached.
                    IoUtils.safeClose(channel);
                    throw new WriteTimeoutException();
                }
                total += result;
            }
            return total;
        } else {
            return Channels.writeBlocking(channel, buffers, offs, len);
        }
    }

    /**
     * Same as writeBlocking with two exceptions: A timeout exception is thrown if a write does not succeed
     * within the time limit, and non-positive values do not apply a timeout.
     * Note that the timeout applies between writes, execution may take longer as long as writes
     * succeed within the specified time.
     * When the timeout is exceeded and an exception is thrown, the provided channel is closed.
     * @throws WriteTimeoutException when a write does not succeed before the timeout is exceeded.
     */
    public static <C extends WritableByteChannel & SuspendableWriteChannel> int writeBlockingOrThrow(
            C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        if (time > 0) {
            int total = 0;
            while (buffer.hasRemaining()) {
                int result = writeBlocking(channel, buffer, time, unit);
                if (result == 0 && buffer.hasRemaining()) {
                    // readBlocking returns zero if either the input does not have any available space
                    // or the timeout was reached.
                    IoUtils.safeClose(channel);
                    throw new WriteTimeoutException();
                }
                total += result;
            }
            return total;
        } else {
            return Channels.writeBlocking(channel, buffer);
        }
    }
}
