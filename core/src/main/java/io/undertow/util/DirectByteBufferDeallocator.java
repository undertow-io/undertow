/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

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
                tmpCleaner = Class.forName("java.nio.DirectByteBuffer").getMethod("cleaner");
                tmpCleaner.setAccessible(true);
                tmpCleanerClean = Class.forName("sun.misc.Cleaner").getMethod("clean");
                tmpCleanerClean.setAccessible(true);
                supported = true;
            } catch (Throwable t) {
                UndertowConnectorLogger.ROOT_LOGGER.directBufferDeallocatorInitializationFailed(t);
                supported = false;
            }
        } else {
            tmpUnsafe = getUnsafe();
            try {
                tmpCleanerClean = tmpUnsafe.getClass().getDeclaredMethod("invokeCleaner", ByteBuffer.class);
                tmpCleanerClean.setAccessible(true);
                supported = true;
            } catch (Throwable t) {
                UndertowConnectorLogger.ROOT_LOGGER.directBufferDeallocatorInitializationFailed(t);
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
                UndertowConnectorLogger.ROOT_LOGGER.directBufferDeallocationFailed(t);
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
}
