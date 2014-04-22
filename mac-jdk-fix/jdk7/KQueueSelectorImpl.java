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
 * KQueueSelectorImpl.java
 * Implementation of Selector using FreeBSD / Mac OS X kqueues
 * Derived from Sun's DevPollSelectorImpl
 */

package sun.nio.ch;

import java.io.IOException;
import java.io.FileDescriptor;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;
import sun.misc.*;

class KQueueSelectorImpl
    extends SelectorImpl
{
    // File descriptors used for interrupt
    protected int fd0;
    protected int fd1;

    // The kqueue manipulator
    KQueueArrayWrapper kqueueWrapper;

    // Count of registered descriptors (including interrupt)
    private int totalChannels;

    // Map from a file descriptor to an entry containing the selection key
    private HashMap<Integer,MapEntry> fdMap;

    // True if this Selector has been closed
    private boolean closed = false;

    // Lock for interrupt triggering and clearing
    private Object interruptLock = new Object();
    private boolean interruptTriggered = false;

    // used by updateSelectedKeys to handle cases where the same file
    // descriptor is polled by more than one filter
    private long updateCount;

    // Used to map file descriptors to a selection key and "update count"
    // (see updateSelectedKeys for usage).
    private static class MapEntry {
        SelectionKeyImpl ski;
        long updateCount;
        MapEntry(SelectionKeyImpl ski) {
            this.ski = ski;
        }
    }

    /**
     * Package private constructor called by factory method in
     * the abstract superclass Selector.
     */
    KQueueSelectorImpl(SelectorProvider sp) {
        super(sp);
        long fds = IOUtil.makePipe(false);
        fd0 = (int)(fds >>> 32);
        fd1 = (int)fds;
        kqueueWrapper = new KQueueArrayWrapper();
        kqueueWrapper.initInterrupt(fd0, fd1);
        fdMap = new HashMap<Integer,MapEntry>();
        totalChannels = 1;
    }


    protected int doSelect(long timeout)
        throws IOException
    {
        int entries = 0;
        if (closed)
            throw new ClosedSelectorException();
        processDeregisterQueue();
        try {
            begin();
            entries = kqueueWrapper.poll(timeout);
        } finally {
            end();
        }
        processDeregisterQueue();
        return updateSelectedKeys(entries);
    }

    /**
     * Update the keys whose fd's have been selected by kqueue.
     * Add the ready keys to the selected key set.
     * If the interrupt fd has been selected, drain it and clear the interrupt.
     */
    private int updateSelectedKeys(int entries)
        throws IOException
    {
        int numKeysUpdated = 0;
        boolean interrupted = false;

        // A file descriptor may be registered with kqueue with more than one
        // filter and so there may be more than one event for a fd. The update
        // count in the MapEntry tracks when the fd was last updated and this
        // ensures that the ready ops are updated rather than replaced by a
        // second or subsequent event.
        updateCount++;

        for (int i = 0; i < entries; i++) {
            int nextFD = kqueueWrapper.getDescriptor(i);
            if (nextFD == fd0) {
                interrupted = true;
            } else {
                MapEntry me = fdMap.get(Integer.valueOf(nextFD));

                // entry is null in the case of an interrupt
                if (me != null) {
                    int rOps = kqueueWrapper.getReventOps(i);
                    SelectionKeyImpl ski = me.ski;
                    if (selectedKeys.contains(ski)) {
                        // first time this file descriptor has been encountered on this
                        // update?
                        if (me.updateCount != updateCount) {
                            if (ski.channel.translateAndSetReadyOps(rOps, ski)) {
                                numKeysUpdated++;
                                me.updateCount = updateCount;
                            }
                        } else {
                            // ready ops have already been set on this update
                            ski.channel.translateAndUpdateReadyOps(rOps, ski);
                        }
                    } else {
                        ski.channel.translateAndSetReadyOps(rOps, ski);
                        if ((ski.nioReadyOps() & ski.nioInterestOps()) != 0) {
                            selectedKeys.add(ski);
                            numKeysUpdated++;
                            me.updateCount = updateCount;
                        }
                    }
                }
            }
        }

        if (interrupted) {
            // Clear the wakeup pipe
            synchronized (interruptLock) {
                IOUtil.drain(fd0);
                interruptTriggered = false;
            }
        }
        return numKeysUpdated;
    }


    protected void implClose() throws IOException {
        if (!closed) {
            closed = true;

            // prevent further wakeup
            synchronized (interruptLock) {
                interruptTriggered = true;
            }

            FileDispatcherImpl.closeIntFD(fd0);
            FileDispatcherImpl.closeIntFD(fd1);
            if (kqueueWrapper != null) {
                kqueueWrapper.close();
                kqueueWrapper = null;
                selectedKeys = null;

                // Deregister channels
                Iterator<SelectionKey> i = keys.iterator();
                while (i.hasNext()) {
                    SelectionKeyImpl ski = (SelectionKeyImpl)i.next();
                    deregister(ski);
                    SelectableChannel selch = ski.channel();
                    if (!selch.isOpen() && !selch.isRegistered())
                        ((SelChImpl)selch).kill();
                    i.remove();
                }
                totalChannels = 0;
            }
            fd0 = -1;
            fd1 = -1;
        }
    }


    protected void implRegister(SelectionKeyImpl ski) {
        if (closed)
            throw new ClosedSelectorException();
        int fd = IOUtil.fdVal(ski.channel.getFD());
        fdMap.put(Integer.valueOf(fd), new MapEntry(ski));
        totalChannels++;
        keys.add(ski);
    }


    protected void implDereg(SelectionKeyImpl ski) throws IOException {
        int fd = ski.channel.getFDVal();
        fdMap.remove(Integer.valueOf(fd));
        kqueueWrapper.release(ski.channel);
        totalChannels--;
        keys.remove(ski);
        selectedKeys.remove(ski);
        deregister((AbstractSelectionKey)ski);
        SelectableChannel selch = ski.channel();
        if (!selch.isOpen() && !selch.isRegistered())
            ((SelChImpl)selch).kill();
    }


    public void putEventOps(SelectionKeyImpl ski, int ops) {
        if (closed)
            throw new ClosedSelectorException();
        kqueueWrapper.setInterest(ski.channel, ops);
    }


    public Selector wakeup() {
        synchronized (interruptLock) {
            if (!interruptTriggered) {
                kqueueWrapper.interrupt();
                interruptTriggered = true;
            }
        }
        return this;
    }


    static {
        Util.load();
    }
}

