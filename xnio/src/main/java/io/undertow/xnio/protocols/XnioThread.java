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

package io.undertow.xnio.protocols;

import java.util.concurrent.TimeUnit;

import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;

import io.undertow.connector.IoExecutor;

public class XnioThread implements IoExecutor {

    final XnioIoThread ioThread;

    public XnioThread(XnioIoThread ioThread) {
        this.ioThread = ioThread;
    }

    @Override
    public void execute(Runnable command) {
        ioThread.execute(command);
    }

    @Override
    public boolean isCurrentThread() {
        return Thread.currentThread() == ioThread;
    }

    @Override
    public Key executeAfter(Runnable task, long timeout, TimeUnit timeUnit) {
        XnioExecutor.Key key = ioThread.executeAfter(task, timeout, timeUnit);
        return new Key() {
            @Override
            public boolean remove() {
                return key.remove();
            }
        };
    }

    @Override
    public boolean isShutdown() {
        return ioThread.getWorker().isShutdown();
    }

    public XnioIoThread getIoThread() {
        return ioThread;
    }
}
