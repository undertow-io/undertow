package io.undertow.testutils;

import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Stuart Douglas
 */
public class DebuggingSlicePool implements ByteBufferPool{

    /**
     * context that can be added to allocations to give more information about buffer leaks, useful when debugging buffer leaks
     */
    private static final ThreadLocal<String> ALLOCATION_CONTEXT = new ThreadLocal<>();

    static final Set<DebuggingBuffer> BUFFERS = Collections.newSetFromMap(new ConcurrentHashMap<DebuggingBuffer, Boolean>());
    static volatile String currentLabel;

    private final ByteBufferPool delegate;
    private final ByteBufferPool arrayBacked;


    public DebuggingSlicePool(ByteBufferPool delegate) {
        this.delegate = delegate;
        if(delegate.isDirect()) {
            this.arrayBacked = new DebuggingSlicePool(delegate.getArrayBackedPool());
        } else {
            this.arrayBacked = this;
        }
    }

    public static void addContext(String context) {
        ALLOCATION_CONTEXT.set(context);
    }

    @Override
    public PooledByteBuffer allocate() {
        final PooledByteBuffer delegate = this.delegate.allocate();
        return new DebuggingBuffer(delegate, currentLabel);
    }

    @Override
    public ByteBufferPool getArrayBackedPool() {
        return arrayBacked;
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public int getBufferSize() {
        return delegate.getBufferSize();
    }

    @Override
    public boolean isDirect() {
        return delegate.isDirect();
    }

    static class DebuggingBuffer implements PooledByteBuffer {

        private static final AtomicInteger allocationCount = new AtomicInteger();
        private final RuntimeException allocationPoint;
        private final PooledByteBuffer delegate;
        private final String label;
        private final int no;
        private volatile boolean free = false;
        private RuntimeException freePoint;

        DebuggingBuffer(PooledByteBuffer delegate, String label) {
            this.delegate = delegate;
            this.label = label;
            this.no = allocationCount.getAndIncrement();
            String ctx = ALLOCATION_CONTEXT.get();
            ALLOCATION_CONTEXT.remove();
            allocationPoint = new RuntimeException(delegate.getBuffer()  + " NO: " + no + " " + (ctx == null ? "[NO_CONTEXT]" : ctx));
            BUFFERS.add(this);
        }

        @Override
        public void close() {
            if(free) {
                return;
            }
            freePoint = new RuntimeException("FREE POINT");
            free = true;
            BUFFERS.remove(this);
            delegate.close();
        }

        @Override
        public boolean isOpen() {
            return !free;
        }

        @Override
        public ByteBuffer getBuffer() throws IllegalStateException {
            if(free) {
                throw new IllegalStateException("Buffer already freed, free point: ", freePoint);
            }
            return delegate.getBuffer();
        }

        RuntimeException getAllocationPoint() {
            return allocationPoint;
        }

        String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return "[debug:"+no+"]" + delegate.toString() ;
        }
    }
}
