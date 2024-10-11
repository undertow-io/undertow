package io.undertow.benchmarks;

import java.io.IOException;
import java.nio.ByteBuffer;

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
            int i = start;
            while (i < end) {
                final int bufferPos = buffer.position();
                final int bufferRemaining = buffer.remaining();
                final int sRemaining = end - i;
                final int remaining = Math.min(sRemaining, bufferRemaining);
                final int written = setAsciiBE(buffer, bufferPos, chars, i, remaining);
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

        private static int setAsciiBE(ByteBuffer buffer, int out, char[] chars, int off, int len) {
            final int longRounds = len >>> 3;
            for (int i = 0; i < longRounds; i++) {
                final long batch1 = (((long) chars[off]) << 48) |
                        (((long) chars[off + 2]) << 32) |
                        chars[off + 4] << 16 |
                        chars[off + 6];
                final long batch2 = (((long) chars[off + 1]) << 48) |
                        (((long) chars[off + 3]) << 32) |
                        chars[off + 5] << 16 |
                        chars[off + 7];
                if (((batch1 | batch2) & 0xff80_ff80_ff80_ff80L) != 0) {
                    return i;
                }
                final long batch = (batch1 << 8) | batch2;
                buffer.putLong(out, batch);
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
