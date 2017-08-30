package io.undertow.server;

import io.undertow.UndertowLogger;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * {@link DirectByteBufferDeallocator} Utility class used to free direct buffer memory.
 */
public final class DirectByteBufferDeallocator {
    private static final boolean SUPPORTED;
    private static final Method cleaner;
    private static final Method cleanerClean;

    static {
        Method tmpCleaner = null;
        Method tmpCleanerClean = null;
        boolean supported;
        try {
            tmpCleaner = Class.forName("java.nio.DirectByteBuffer").getMethod("cleaner");
            tmpCleaner.setAccessible(true);
            tmpCleanerClean = Class.forName("sun.misc.Cleaner").getMethod("clean");
            tmpCleanerClean.setAccessible(true);
            supported = true;
        } catch (Throwable t) {
            UndertowLogger.ROOT_LOGGER.directBufferDeallocatorInitializationFailed(t);
            supported = false;
        }
        SUPPORTED = supported;
        cleaner = tmpCleaner;
        cleanerClean = tmpCleanerClean;
    }

    private DirectByteBufferDeallocator() {
        // Utility Class
    }

    /**
     * Attempts to deallocate the underlying direct memory.
     * This is a no-op for buffers where {@link ByteBuffer#isDirect()} returns false.
     *
     * @param buffer to deallocate
     */
    public static void free(ByteBuffer buffer) {
        if (SUPPORTED && buffer != null && buffer.isDirect()) {
            try {
                Object cleaner = DirectByteBufferDeallocator.cleaner.invoke(buffer);
                cleanerClean.invoke(cleaner);
            } catch (Throwable t) {
                UndertowLogger.ROOT_LOGGER.directBufferDeallocationFailed(t);
            }
        }
    }
}
