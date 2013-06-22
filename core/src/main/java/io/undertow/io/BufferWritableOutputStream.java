package io.undertow.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Represents an output stream that can write byte buffers
 * directly.
 *
 * @author Stuart Douglas
 */
public interface BufferWritableOutputStream {

    void write(final ByteBuffer[] buffers) throws IOException;

    void write(final ByteBuffer byteBuffer) throws IOException;

    void transferFrom(FileChannel source) throws IOException;

}
