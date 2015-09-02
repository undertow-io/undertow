package io.undertow.websockets.extensions;

import io.undertow.websockets.core.WebSocketChannel;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class CompositeExtensionFunction implements ExtensionFunction {

    private final ExtensionFunction[] delegates;

    private CompositeExtensionFunction(ExtensionFunction... delegates) {
        this.delegates = delegates;
    }

    public static ExtensionFunction compose(List<ExtensionFunction> functions) {
        if (null == functions) {
            return NoopExtensionFunction.instance;
        }
        return compose(functions.toArray(new ExtensionFunction[functions.size()]));
    }

    public static ExtensionFunction compose(ExtensionFunction... functions) {
        if (functions == null || functions.length == 0) {
            return NoopExtensionFunction.instance;
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
    public Pooled<ByteBuffer> transformForWrite(Pooled<ByteBuffer> pooledBuffer, WebSocketChannel channel) throws IOException {
        Pooled<ByteBuffer> result = pooledBuffer;
        for (ExtensionFunction delegate : delegates) {
            result = delegate.transformForWrite(result, channel);
        }
        return result;
    }

    @Override
    public Pooled<ByteBuffer> transformForRead(Pooled<ByteBuffer> pooledBuffer, WebSocketChannel channel, boolean lastFragmentOfFrame) throws IOException {
        Pooled<ByteBuffer> result = pooledBuffer;
        // TODO do we iterate over functions in the opposite order when reading vs writing?
        for (ExtensionFunction delegate : delegates) {
            result = delegate.transformForRead(result, channel, lastFragmentOfFrame);
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
