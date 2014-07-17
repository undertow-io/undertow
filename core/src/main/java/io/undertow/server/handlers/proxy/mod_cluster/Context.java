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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy.mod_cluster;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 *
 * @author Emanuel Muckenhuber
 */
class Context {

    private static final AtomicInteger idGen = new AtomicInteger();

    static enum Status {

        ENABLED,
        DISABLED,
        STOPPED,
        ;

    }

    private final int id;
    private final Node node;
    private final String path;
    private final Node.VHostMapping vhost;

    private static final int STOPPED = (1 << 31);
    private static final int DISABLED = (1 << 30);
    private static final int REQUEST_MASK = ((1 << 30) - 1);

    private volatile int state = STOPPED;
    private static final AtomicIntegerFieldUpdater<Context> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(Context.class, "state");

    Context(final String path, final Node.VHostMapping vHost, final Node node) {
        id = idGen.incrementAndGet();
        this.path = path;
        this.node = node;
        this.vhost = vHost;
    }

    public int getId() {
        return id;
    }

    public String getJVMRoute() {
        return node.getJvmRoute();
    }

    public String getPath() {
        return path;
    }

    public List<String> getVirtualHosts() {
        return vhost.getAliases();
    }

    public int getActiveRequests() {
        return state & REQUEST_MASK;
    }

    public Status getStatus() {
        final int state = this.state;
        if ((state & STOPPED) == STOPPED) {
            return Status.STOPPED;
        } else if ((state & DISABLED) == DISABLED) {
            return Status.DISABLED;
        }
        return Status.ENABLED;
    }

    public boolean isEnabled() {
        return allAreClear(state, DISABLED | STOPPED);
    }

    public boolean isStopped() {
        return allAreSet(state, STOPPED);
    }

    public boolean isDisabled() {
        return allAreSet(state, DISABLED);
    }

    Node getNode() {
        return node;
    }

    Node.VHostMapping getVhost() {
        return vhost;
    }

    boolean checkAvailable(boolean existingSession) {
        if (node.checkAvailable(existingSession)) {
            return existingSession ? !isStopped() : isEnabled();
        }
        return false;
    }

    void enable() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState & ~(STOPPED | DISABLED);
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                return;
            }
        }
    }

    void disable() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState | DISABLED;
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                return;
            }
        }
    }

    void stop() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState | STOPPED;
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                return;
            }
        }
    }

    boolean addRequest(boolean existingSession) {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            if ((oldState & STOPPED) != 0) {
                return false;
            }
//            if (!existingSession && (oldState & DISABLED) != 0) {
//                return false;
//            }
            newState = oldState + 1;
            if ((newState & REQUEST_MASK) == REQUEST_MASK) {
                return false;
            }
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                return true;
            }
        }
    }

    void requestDone() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            if ((oldState & REQUEST_MASK) == 0) {
                return;
            }
            newState = oldState - 1;
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                return;
            }
        }
    }

}
