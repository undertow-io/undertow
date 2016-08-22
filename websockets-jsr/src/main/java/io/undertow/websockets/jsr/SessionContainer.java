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

package io.undertow.websockets.jsr;

import javax.websocket.Session;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Stuart Douglas
 */
public class SessionContainer {

    private Runnable doneTask;
    private volatile int waiterCount;
    private final Set<Session> openSessions = Collections.newSetFromMap(new ConcurrentHashMap<Session, Boolean>());

    public Set<Session> getOpenSessions() {
        return Collections.unmodifiableSet(openSessions);
    }

    public void addOpenSession(Session session) {
        synchronized (this) {
            openSessions.add(session);
        }
    }

    public void removeOpenSession(Session session) {
        synchronized (this) {
            openSessions.remove(session);
            if (waiterCount > 0 && openSessions.isEmpty()) {
                notifyAll();
            }
            if(doneTask != null) {
                doneTask.run();
                doneTask = null;
            }
        }
    }

    public void awaitClose(long timeout) {
        synchronized (this) {
            if(openSessions.isEmpty()) {
                return;
            }
            waiterCount++;
            long end = System.currentTimeMillis() + timeout;
            try {
                while (System.currentTimeMillis() < end) {
                    wait(end - System.currentTimeMillis());
                }
            } catch (InterruptedException e) {
                //ignore
            } finally {
                waiterCount--;
            }
        }
    }

    public void notifyClosed(Runnable done) {
        synchronized (this) {
            if(openSessions.isEmpty()) {
                done.run();
            } else {
                this.doneTask = done;
            }
        }
    }
}
