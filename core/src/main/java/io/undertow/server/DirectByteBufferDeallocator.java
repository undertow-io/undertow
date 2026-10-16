package io.undertow.server;

import io.undertow.UndertowLogger;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link DirectByteBufferDeallocator} Utility class used to free direct buffer memory.
 *
 * Striping logic was adapted from Guava's Striped
 */
public final class DirectByteBufferDeallocator {
    private static final int DEALLOCATION_DELAY_MILLIS = 100;
    private static final boolean SUPPORTED;
    private static final Method cleaner;
    private static final Method cleanerClean;

    private static final List<ConcurrentLinkedQueue<QueuedByteBuffer>> queues;
    private static final List<Lock> queueLocks;
    private static final int queueCount;
    private static final int queueMask;

    private static final Unsafe UNSAFE;


    static {
        // Round to nearest power of 2 for efficient bitwise operations
        queueCount = roundToPowerOfTwo(Runtime.getRuntime().availableProcessors() * 2);
        queueMask = queueCount - 1;

        List<ConcurrentLinkedQueue<QueuedByteBuffer>> tmpQueues = new ArrayList<>(queueCount);
        List<Lock> tmpLocks = new ArrayList<>(queueCount);
        for (int i = 0; i < queueCount; i++) {
            tmpQueues.add(new ConcurrentLinkedQueue<>());
            tmpLocks.add(new ReentrantLock());
        }
        queues = Collections.unmodifiableList(tmpQueues);
        queueLocks = Collections.unmodifiableList(tmpLocks);

        String versionString = System.getProperty("java.specification.version");
        if(versionString.equals("0.9")) {
            //android hardcoded
            versionString = "11";
        } else if(versionString.startsWith("1.")) {
            versionString = versionString.substring(2);
        }
        int version = Integer.parseInt(versionString);

        Method tmpCleaner = null;
        Method tmpCleanerClean = null;
        boolean supported;
        Unsafe tmpUnsafe = null;
        if (version < 9) {
            try {
                tmpCleaner = getAccesibleMethod("java.nio.DirectByteBuffer", "cleaner");
                tmpCleanerClean = getAccesibleMethod("sun.misc.Cleaner", "clean");
                supported = true;
            } catch (Throwable t) {
                UndertowLogger.ROOT_LOGGER.directBufferDeallocatorInitializationFailed(t);
                supported = false;
            }
        } else {
            try {
                tmpUnsafe = getUnsafe();
                tmpCleanerClean = getDeclaredMethod(tmpUnsafe, "invokeCleaner", ByteBuffer.class);
                supported = true;
            } catch (Throwable t) {
                UndertowLogger.ROOT_LOGGER.directBufferDeallocatorInitializationFailed(t);
                supported = false;
            }
        }
        SUPPORTED = supported;
        cleaner = tmpCleaner;
        cleanerClean = tmpCleanerClean;
        UNSAFE = tmpUnsafe;

    }

    private DirectByteBufferDeallocator() {
        // Utility Class
    }

    private static int getQueueIndex() {
        return getQueueIndex(Thread.currentThread().getId(), queueMask);
    }

    private static int getQueueIndex(long threadId, int mask) {
        int threadHash = (int) threadId;
        return smear(threadHash) & mask;
    }

    /**
     * Smears the hash code to spread high-order bits into low-order bits, improving distribution.
     * Based on Doug Lea's algorithm from OpenJDK HashMap.
     */
    private static int smear(int hashCode) {
        int hash = hashCode;
        hash ^= (hash >>> 20) ^ (hash >>> 12);
        return hash ^ (hash >>> 7) ^ (hash >>> 4);
    }

    private static int roundToPowerOfTwo(int value) {
        if (value <= 0) {
            return 1;
        }
        // Cap at 2^30 (max safe power of 2 for positive int)
        if (value > (1 << 30)) {
            return 1 << 30;
        }
        // Round up to next power of 2
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
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
                int queueIdx = getQueueIndex();
                final ConcurrentLinkedQueue<QueuedByteBuffer> queue = queues.get(queueIdx);
                final Lock lock = queueLocks.get(queueIdx);

                // Try to clean old buffers if we can acquire the lock
                // If another thread is already cleaning, skip it to avoid contention
                if (lock.tryLock()) {
                    try {
                        cleanOldBuffersFromQueue(queue);
                    } finally {
                        lock.unlock();
                    }
                }

                // Put the buffer to be cleaned in the queue
                // The goal here is to create a delay to make sure
                // that the buffer is not immediately deallocated
                // as there is a small window of time in which the
                // buffer is still accessible via local variables;
                // if a direct buffer is cleaned and then written to
                // or read from, the behavior of the sdk is unpredictable
                queue.add(new QueuedByteBuffer(buffer));
            } catch (Throwable t) {
                UndertowLogger.ROOT_LOGGER.directBufferDeallocationFailed(t);
            }
        }
    }

    /**
     * Cleans buffers from the queue that have been waiting at least DEALLOCATION_DELAY_MILLIS.
     */
    private static void cleanOldBuffersFromQueue(ConcurrentLinkedQueue<QueuedByteBuffer> queue)
            throws InvocationTargetException, IllegalAccessException {
        final long targetTimeMillis = System.currentTimeMillis() - DEALLOCATION_DELAY_MILLIS;
        QueuedByteBuffer queuedByteBuffer = queue.peek();
        while (queuedByteBuffer != null) {
            if (queuedByteBuffer.timeStamp > targetTimeMillis) {
                break;
            }
            queue.remove();
            cleanBuffer(queuedByteBuffer.byteBuffer);
            queuedByteBuffer = queue.peek();
        }
    }

    private static void cleanBuffer(ByteBuffer buffer) throws InvocationTargetException, IllegalAccessException {
        if (buffer != null) {
            if (UNSAFE != null) {
                //use the JDK9 method
                cleanerClean.invoke(UNSAFE, buffer);
            } else {
                Object cleaner = DirectByteBufferDeallocator.cleaner.invoke(buffer);
                cleanerClean.invoke(cleaner);
            }
        }
    }

    private static Unsafe getUnsafe() {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(new PrivilegedAction<Unsafe>() {
                public Unsafe run() {
                    return getUnsafe0();
                }
            });
        }
        return getUnsafe0();
    }

    private static Unsafe getUnsafe0() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (Throwable t) {
            throw new RuntimeException("JDK did not allow accessing unsafe", t);
        }
    }

    private static Method getAccesibleMethod(String className, String methodName) {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(new PrivilegedAction<Method>() {
                @Override
                public Method run() {
                    return getAccesibleMethod0(className, methodName);
                }
            });
        }
        return getAccesibleMethod0(className, methodName);
    }

    private static Method getAccesibleMethod0(String className, String methodName) {
        try {
            Method method = Class.forName(className).getMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            throw new RuntimeException("JDK did not allow accessing method", t);
        }
    }

    private static Method getDeclaredMethod(Unsafe tmpUnsafe, String methodName, Class<?>... parameterTypes) {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(new PrivilegedAction<Method>() {
                @Override
                public Method run() {
                    return getDeclaredMethod0(tmpUnsafe, methodName, parameterTypes);
                }
            });
        }
        return getDeclaredMethod0(tmpUnsafe, methodName, parameterTypes);
    }

    private static Method getDeclaredMethod0(Unsafe tmpUnsafe, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = tmpUnsafe.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            throw new RuntimeException("JDK did not allow accessing method", t);
        }
    }

    private static class QueuedByteBuffer {
        final long timeStamp;
        final ByteBuffer byteBuffer;

        QueuedByteBuffer(ByteBuffer byteBuffer) {
            this.timeStamp = System.currentTimeMillis();
            this.byteBuffer = byteBuffer;
        }
    }
}
