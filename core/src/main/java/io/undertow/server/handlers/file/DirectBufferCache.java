package io.undertow.server.handlers.file;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.IllegalFormatFlagsException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.xml.internal.ws.util.Pool;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pooled;


/**
 * @author Jason T. Greene
 */
public class DirectBufferCache {
    @SuppressWarnings("unchecked")
    private static final Pooled<ByteBuffer>[] EMPTY_BUFFERS = new Pooled[0];

    private final ByteBufferSlicePool pool;
    private final AtomicInteger use = new AtomicInteger();
    private final int max;
    private final int sliceSize;
    private final int segmentShift;
    private final Segment[] segments;

    public DirectBufferCache(int sliceSize, int max) {
        this(sliceSize, max, Runtime.getRuntime().availableProcessors() * 2);
    }

    public DirectBufferCache(int sliceSize, int max, int concurrency) {
        this.sliceSize = sliceSize;
        this.max = max;
        this.pool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, sliceSize, max);
        int shift = 1;
        while (concurrency < (shift <<= 1));
        segmentShift = 32 - shift;
        segments = new Segment[shift];
        for (int i = 0; i < 1; i++) {
            segments[i] = new Segment(shift);
        }
    }

    private static int hash(int h) {
        // Spread bits to regularize both segment and index locations,
        // using variant of single-word Wang/Jenkins hash.
        h += (h <<  15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h <<   3);
        h ^= (h >>>  6);
        h += (h <<   2) + (h << 14);
        return h ^ (h >>> 16);
    }

    public CacheEntry add(String path, int size) {
        Segment[] segments = this.segments;
        return segments[hash(path.hashCode()) >>> segmentShift & (segments.length - 1)].add(path, size);
    }

    public CacheEntry get(String path) {
        Segment[] segments = this.segments;
        return segments[hash(path.hashCode()) >>> segmentShift & (segments.length - 1)].get(path);
    }

    private static class MaxLinkedMap<K, V> extends LinkedHashMap<K, V> {
        private int max;

        public MaxLinkedMap(int max) {
            super(3, 0.66f, false);
            this.max = max;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > max;
        }
    }

    private static class CacheMap extends MaxLinkedMap<String, CacheEntry> {
        private CacheMap(int max) {
            super(max);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            CacheEntry value = eldest.getValue();
            value.destroy();
            return super.removeEldestEntry(eldest);
        }
    }

    public class CacheEntry {
        private final int size;
        private volatile Pooled<ByteBuffer>[] buffers;
        private volatile boolean enabled;
        private volatile long time;

        public CacheEntry(int size, Pooled<ByteBuffer>[] buffers) {
            this.size = size;
            this.buffers = buffers;
        }

        public int size() {
            return size;
        }

        public Pooled<ByteBuffer>[] buffers() {
            return buffers;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public long time() {
            return time;
        }

        public void setTime(int time) {
            this.time  = time;
        }

        public void enable() {
            this.enabled = true;
        }

        public void destroy() {
            enabled = false;

            for (Pooled<ByteBuffer> buffer : buffers()) {
                buffer.free();
                use.getAndAdd(-sliceSize);
            }
            buffers = EMPTY_BUFFERS;
        }

    }

    private class Segment {
        private final LinkedHashMap<String, CacheEntry> cache;
        private final LinkedHashMap<String, Integer> candidates;

        private Segment(int concurrency) {
            int limit = Math.max(100, max / sliceSize / concurrency);
            cache = new CacheMap(limit);
            candidates = new MaxLinkedMap<String, Integer>(limit);
        }

        public synchronized CacheEntry get(String path) {
            return cache.get(path);
        }

        public synchronized CacheEntry add(String path, int size) {
            CacheEntry entry = cache.get(path);
            if (entry != null)
                return null;

            Integer i = candidates.get(path);
            int count = i == null ? 0 : i.intValue();

            if (count > 5) {
                candidates.remove(path);
                entry = addCacheEntry(path, size);
            }  else {
                candidates.put(path, Integer.valueOf(count++));
            }

            return entry;
        }

        private boolean reserveSpace(int size) {
            boolean reserved = false;
            while (!reserved) {
                int inUse = use.get();
                if (inUse + size > max) {
                    return false;
                }

                reserved = use.compareAndSet(inUse, inUse + size);
            }

            return true;
        }

        private CacheEntry addCacheEntry(String path, int size) {
            int reserveSize = sliceSize;
            while (reserveSize < size) {
                reserveSize += sliceSize;
            }

            Iterator<CacheEntry> iterator = cache.values().iterator();
            boolean reserved = reserveSpace(reserveSize);
            while (!reserved && iterator.hasNext()) {
                CacheEntry value = iterator.next();
                iterator.remove();
                value.destroy();
                reserved = reserveSpace(reserveSize);
            }

            if (!reserved) {
                return null;
            }

            int num = reserveSize / sliceSize;
            @SuppressWarnings("unchecked")
            Pooled<ByteBuffer>[] buffers = new Pooled[num];
            for (int i = 0; i < num; i++) {
                buffers[i] = pool.allocate();
            }

            CacheEntry result = new CacheEntry(size, buffers);
            cache.put(path, result);

            return result;

        }
    }
}
