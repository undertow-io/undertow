package io.undertow.websockets.extensions;

import io.undertow.websockets.core.WebSocketChannel;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;

public class NoopExtensionFunction implements ExtensionFunction {
    public static final ExtensionFunction instance = new NoopExtensionFunction();

    @Override
    public boolean hasExtensionOpCode() {
        return false;
    }

    @Override
    public int writeRsv(int rsv) {
        return 0;
    }

    @Override
    public Pooled<ByteBuffer> transformForWrite(Pooled<ByteBuffer> pooledBuffer, WebSocketChannel channel) throws IOException {
        return pooledBuffer;
    }

    @Override
    public Pooled<ByteBuffer> transformForRead(Pooled<ByteBuffer> pooledBuffer, WebSocketChannel channel, boolean lastFragmentOfFrame) throws IOException {
        return pooledBuffer;
    }

    @Override
    public void dispose() {

    }
}
