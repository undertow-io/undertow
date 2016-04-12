package io.undertow.websockets.extensions;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.StreamSourceFrameChannel;

import java.io.IOException;
import java.util.List;

public class CompositeExtensionFunction implements ExtensionFunction {

    private final ExtensionFunction[] delegates;

    private CompositeExtensionFunction(ExtensionFunction... delegates) {
        this.delegates = delegates;
    }

    public static ExtensionFunction compose(List<ExtensionFunction> functions) {
        if (null == functions) {
            return NoopExtensionFunction.INSTANCE;
        }
        return compose(functions.toArray(new ExtensionFunction[functions.size()]));
    }

    public static ExtensionFunction compose(ExtensionFunction... functions) {
        if (functions == null || functions.length == 0) {
            return NoopExtensionFunction.INSTANCE;
        } else if (functions.length == 1) {
            return functions[0];
        }

        return new CompositeExtensionFunction(functions);
    }

    @Override
    public boolean hasExtensionOpCode() {
        for (ExtensionFunction delegate : delegates) {
            if (delegate.hasExtensionOpCode()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int writeRsv(int rsv) {
        for (ExtensionFunction ext : delegates) {
            rsv = ext.writeRsv(rsv);
        }

        return rsv;
    }

    @Override
    public PooledByteBuffer transformForWrite(PooledByteBuffer pooledBuffer, StreamSinkFrameChannel channel, boolean lastFrame) throws IOException {
        PooledByteBuffer result = pooledBuffer;
        for (ExtensionFunction delegate : delegates) {
            result = delegate.transformForWrite(result, channel, lastFrame);
        }
        return result;
    }

    @Override
    public PooledByteBuffer transformForRead(PooledByteBuffer pooledBuffer, StreamSourceFrameChannel channel, boolean lastFragementOfMessage) throws IOException {
        PooledByteBuffer result = pooledBuffer;
        // TODO do we iterate over functions in the opposite order when reading vs writing?
        for (ExtensionFunction delegate : delegates) {
            result = delegate.transformForRead(result, channel, lastFragementOfMessage);
        }
        return result;
    }

    @Override
    public void dispose() {
        for (ExtensionFunction delegate : delegates) {
            delegate.dispose();
        }
    }
}
