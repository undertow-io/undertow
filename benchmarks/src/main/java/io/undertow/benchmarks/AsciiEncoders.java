package io.undertow.benchmarks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AsciiEncoders {

    public interface BufferFlusher {
        void flushBuffer(ByteBuffer buffer) throws IOException;
    }

    public interface AsciiEncoder {

        int writeAndFlushAscii(BufferFlusher flusher, ByteBuffer buffer, char[] chars, int start, int end) throws IOException;

    }

    public enum BatchFixedBufferOffsetAsciiEncoder implements AsciiEncoder {
        Instance;

        @Override
        public int writeAndFlushAscii(BufferFlusher flusher, ByteBuffer buffer, char[] chars, int start, int end) throws IOException {
            final ByteOrder order = buffer.order();
            int i = start;
            while (i < end) {
                final int bufferPos = buffer.position();
                final int bufferRemaining = buffer.remaining();
                final int sRemaining = end - i;
                final int remaining = Math.min(sRemaining, bufferRemaining);
                final int written = order == ByteOrder.LITTLE_ENDIAN ?
                        setAsciiLE(buffer, bufferPos, chars, i, remaining) :
                        setAsciiBE(buffer, bufferPos, chars, i, remaining);
                i += written;
                buffer.position(bufferPos + written);
                if (!buffer.hasRemaining()) {
                    flusher.flushBuffer(buffer);
                }
                // we have given up with the fast path? slow path NOW!
                if (written < remaining) {
                    return i;
                }
            }
            return i;
        }

        private static int setAsciiLE(ByteBuffer buffer, int out, char[] chars, int off, int len) {
            final int longRounds = len >>> 3;
            for (int i = 0; i < longRounds; i++) {
                final char c0 = chars[off];
                final char c1 = chars[off + 1];
                final char c2 = chars[off + 2];
                final char c3 = chars[off + 3];
                final char c4 = chars[off + 4];
                final char c5 = chars[off + 5];
                final char c6 = chars[off + 6];
                final char c7 = chars[off + 7];
                if (c7 > 127 || c6 > 127 || c5 > 127 || c4 > 127 ||
                        c3 > 127 || c2 > 127 || c1 > 127 || c0 > 127) {
                    return i << 3;
                }
                final long leBatch = (((long) (c7) << 56) |
                        ((long) (c6 & 0xff) << 48) |
                        ((long) (c5 & 0xff) << 40) |
                        ((long) (c4 & 0xff) << 32) |
                        ((long) (c3 & 0xff) << 24) |
                        ((long) (c2 & 0xff) << 16) |
                        ((long) (c1 & 0xff) << 8) |
                        ((long) (c0 & 0xff)));
                buffer.putLong(out, leBatch);
                out += Long.BYTES;
                off += Long.BYTES;
            }
            final int byteRounds = len & 7;
            if (byteRounds > 0) {
                for (int i = 0; i < byteRounds; i++) {
                    final char c = chars[off + i];
                    if (c > 127) {
                        return (longRounds << 3) + i;
                    }
                    buffer.put(out + i, (byte) c);
                }
            }
            return len;
        }

        private static int setAsciiBE(ByteBuffer buffer, int out, char[] chars, int off, int len) {
            final int longRounds = len >>> 3;
            for (int i = 0; i < longRounds; i++) {
                final char c0 = chars[off];
                final char c1 = chars[off + 1];
                final char c2 = chars[off + 2];
                final char c3 = chars[off + 3];
                final char c4 = chars[off + 4];
                final char c5 = chars[off + 5];
                final char c6 = chars[off + 6];
                final char c7 = chars[off + 7];
                if (c7 > 127 || c6 > 127 || c5 > 127 || c4 > 127 ||
                        c3 > 127 || c2 > 127 || c1 > 127 || c0 > 127) {
                    return i << 3;
                }
                final long leBatch = (((long) (c0) << 56) |
                        ((long) (c1 & 0xff) << 48) |
                        ((long) (c2 & 0xff) << 40) |
                        ((long) (c3 & 0xff) << 32) |
                        ((long) (c4 & 0xff) << 24) |
                        ((long) (c5 & 0xff) << 16) |
                        ((long) (c6 & 0xff) << 8) |
                        ((long) (c7 & 0xff)));
                buffer.putLong(out, leBatch);
                out += Long.BYTES;
                off += Long.BYTES;
            }
            final int byteRounds = len & 7;
            if (byteRounds > 0) {
                for (int i = 0; i < byteRounds; i++) {
                    final char c = chars[off + i];
                    if (c > 127) {
                        return (longRounds << 3) + i;
                    }
                    buffer.put(out + i, (byte) c);
                }
            }
            return len;
        }
    }

    public enum NonBatchFixedBufferOffsetAsciiEncoder implements AsciiEncoder {
        Instance;

        @Override
        public int writeAndFlushAscii(BufferFlusher flusher, ByteBuffer buffer, char[] chars, int start, int end) throws IOException {
            int i = start;
            while (i < end) {
                final int bufferPos = buffer.position();
                final int bufferRemaining = buffer.remaining();
                final int sRemaining = end - i;
                final int remaining = Math.min(sRemaining, bufferRemaining);
                final int written = setAscii(buffer, bufferPos, chars, i, remaining);
                i += written;
                buffer.position(bufferPos + written);
                if (!buffer.hasRemaining()) {
                    flusher.flushBuffer(buffer);
                }
                // we have given up with the fast path? slow path NOW!
                if (written < remaining) {
                    return i;
                }
            }
            return i;
        }

        private static int setAscii(ByteBuffer buffer, int out, char[] chars, int off, int len) {
            for (int i = 0; i < len; i++) {
                final char c = chars[off + i];
                if (c > 127) {
                    return i;
                }
                buffer.put(out + i, (byte) c);
            }
            return len;
        }
    }

    public enum NonBatchMutableBufferOffsetAsciiEncoder implements AsciiEncoder {
        Instance;

        @Override
        public int writeAndFlushAscii(BufferFlusher flusher, ByteBuffer buffer, char[] chars, int start, int end) throws IOException {
            //fast path, basically we are hoping this is ascii only
            int remaining = buffer.remaining();
            boolean ok = true;
            //so we have a pure ascii buffer, just write it out and skip all the encoder cost
            int i = start;
            int flushPos = i + remaining;
            while (ok && i < end) {
                int realEnd = Math.min(end, flushPos);
                for (; i < realEnd; ++i) {
                    char c = chars[i];
                    if (c > 127) {
                        ok = false;
                        break;
                    } else {
                        buffer.put((byte) c);
                    }
                }
                if (i == flushPos) {
                    flusher.flushBuffer(buffer);
                    flushPos = i + buffer.remaining();
                }
            }
            if (ok) {
                return end - start;
            }
            return -1;
        }
    }


}
