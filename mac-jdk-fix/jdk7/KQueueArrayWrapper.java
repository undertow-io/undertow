/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 * KQueueArrayWrapper.java
 * Implementation of Selector using FreeBSD / Mac OS X kqueues
 * Derived from Sun's DevPollArrayWrapper
 */

package sun.nio.ch;

import sun.misc.*;
import java.io.IOException;
import java.io.FileDescriptor;
import java.util.Iterator;
import java.util.LinkedList;

/*
 * struct kevent {           // 32-bit    64-bit
 *     uintptr_t ident;      //   4         8
 *     short     filter;     //   2         2
 *     u_short   flags;      //   2         2
 *     u_int     fflags;     //   4         4
 *     intptr_t  data;       //   4         8
 *     void      *udata;     //   4         8
 * }                  // Total:  20        32
 *
 * The implementation works in 32-bit and 64-bit world. We do this by calling a
 * native function that actually sets the sizes and offsets of the fields based
 * on which mode we're in.
 */

class KQueueArrayWrapper {
    // Event masks
    static final short POLLIN       = AbstractPollArrayWrapper.POLLIN;
    static final short POLLOUT      = AbstractPollArrayWrapper.POLLOUT;

    // kevent filters
    static short EVFILT_READ;
    static short EVFILT_WRITE;

    // kevent struct
    // These fields are now set by initStructSizes in the static initializer.
    static short SIZEOF_KEVENT;
    static short FD_OFFSET;
    static short FILTER_OFFSET;

    // kevent array size
    static final int NUM_KEVENTS = 128;

    // Are we in a 64-bit VM?
    static boolean is64bit = false;

    // The kevent array (used for outcoming events only)
    private AllocatedNativeObject keventArray = null;
    private long keventArrayAddress;

    // The kqueue fd
    private int kq = -1;

    // The fd of the interrupt line going out
    private int outgoingInterruptFD;

    // The fd of the interrupt line coming in
    private int incomingInterruptFD;

    static {
        initStructSizes();
        String datamodel = (String) java.security.AccessController.doPrivileged(
        new sun.security.action.GetPropertyAction("sun.arch.data.model"));
        is64bit = datamodel.equals("64");
    }

    KQueueArrayWrapper() {
        int allocationSize = SIZEOF_KEVENT * NUM_KEVENTS;
        keventArray = new AllocatedNativeObject(allocationSize, true);
        keventArrayAddress = keventArray.address();
        kq = init();
    }

    // Used to update file description registrations
    private static class Update {
        SelChImpl channel; 
        int events;
        Update(SelChImpl channel, int events) {
            this.channel = channel;
            this.events = events;
        }
    }
    
    private LinkedList<Update> updateList = new LinkedList<Update>();

    void initInterrupt(int fd0, int fd1) {
        outgoingInterruptFD = fd1;
        incomingInterruptFD = fd0;
        register0(kq, fd0, 1, 0);
    }

    int getReventOps(int index) {
        int result = 0;
        int offset = SIZEOF_KEVENT*index + FILTER_OFFSET;
        short filter = keventArray.getShort(offset);

        // This is all that's necessary based on inspection of usage:
        //   SinkChannelImpl, SourceChannelImpl, DatagramChannelImpl,
        //   ServerSocketChannelImpl, SocketChannelImpl
        if (filter == EVFILT_READ) {
            result |= POLLIN;
        } else if (filter == EVFILT_WRITE) {
            result |= POLLOUT;
        }

        return result;
    }

    int getDescriptor(int index) {
        int offset = SIZEOF_KEVENT*index + FD_OFFSET;
        /* The ident field is 8 bytes in 64-bit world, however the API wants us
         * to return an int. Hence read the 8 bytes but return as an int.
         */
        if (is64bit) {
          long fd = keventArray.getLong(offset);
          assert fd <= Integer.MAX_VALUE;
          return (int) fd;
        } else {
          return keventArray.getInt(offset);
        }
    }

    void setInterest(SelChImpl channel, int events) {
        synchronized (updateList) {
            // update existing registration
            updateList.add(new Update(channel, events));
        }
    }

    void release(SelChImpl channel) {
        synchronized (updateList) {
            // flush any pending updates
            for (Iterator<Update> it = updateList.iterator(); it.hasNext();) {
                if (it.next().channel == channel) {
                    it.remove();
                }
            }

            // remove
            register0(kq, channel.getFDVal(), 0, 0);
        }
    }

    void updateRegistrations() {
        synchronized (updateList) {
            Update u = null;
            while ((u = updateList.poll()) != null) {
                SelChImpl ch = u.channel;
                if (!ch.isOpen())
                    continue;

                register0(kq, ch.getFDVal(), u.events & POLLIN, u.events & POLLOUT);
            }
        }
    }


    void close() throws IOException {
        if (keventArray != null) {
            keventArray.close();
            keventArray = null;
        }
        if (kq >= 0) {
            FileDispatcherImpl.closeIntFD(kq);
            kq = -1;
        }
    }

    int poll(long timeout) {
        updateRegistrations();
        int updated = kevent0(kq, keventArrayAddress, NUM_KEVENTS, timeout);
        return updated;
    }

    void interrupt() {
        interrupt(outgoingInterruptFD);
    }

    private native int init();
    private static native void initStructSizes();

    private native void register0(int kq, int fd, int read, int write);
    private native int kevent0(int kq, long keventAddress, int keventCount,
                               long timeout);
    private static native void interrupt(int fd);
}

