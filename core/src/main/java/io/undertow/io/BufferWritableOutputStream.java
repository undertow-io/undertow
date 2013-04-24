package io.undertow.io;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents an output stream that can write byte buffers
 * directly.
 *
 * @author Stuart Douglas
 */
public interface BufferWritableOutputStream {

    void write(final ByteBuffer[] buffers) throws IOException;

    void write(final ByteBuffer byteBuffer) throws IOException;

}
