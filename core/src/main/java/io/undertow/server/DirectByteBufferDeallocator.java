package io.undertow.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

import io.undertow.UndertowLogger;
import sun.misc.Unsafe;

/**
 * {@link DirectByteBufferDeallocator} Utility class used to free direct buffer memory.
 */
public final class DirectByteBufferDeallocator {
    private static final boolean SUPPORTED;
    private static final Method cleaner;
    private static final Method cleanerClean;

    private static final Unsafe UNSAFE;


    static {
        String versionString = System.getProperty("java.specification.version");
        if(versionString.startsWith("1.")) {
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

    /**
     * Attempts to deallocate the underlying direct memory.
     * This is a no-op for buffers where {@link ByteBuffer#isDirect()} returns false.
     *
     * @param buffer to deallocate
     */
    public static void free(ByteBuffer buffer) {
        if (SUPPORTED && buffer != null && buffer.isDirect()) {
            try {
                if (UNSAFE != null) {
                    //use the JDK9 method
                    cleanerClean.invoke(UNSAFE, buffer);
                } else {
                    Object cleaner = DirectByteBufferDeallocator.cleaner.invoke(buffer);
                    cleanerClean.invoke(cleaner);
                }
            } catch (Throwable t) {
                UndertowLogger.ROOT_LOGGER.directBufferDeallocationFailed(t);
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
}
