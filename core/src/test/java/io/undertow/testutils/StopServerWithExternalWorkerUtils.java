/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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
package io.undertow.testutils;

import java.net.BindException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.undertow.Undertow;
import org.xnio.XnioWorker;

import static org.junit.Assert.fail;

/**
 * <p>
 * When an Undertow Server has an internal worker (i.e. a {@link org.xnio.XnioWorker}
 * that is created upon start if no worker is {@link io.undertow.Undertow.Builder#setWorker(XnioWorker)
 * provided} to the builder), the worker is automatically closed with the server when it
 * {@link Undertow#stop() stops}. This operation blocks until the worker is finished, and this is
 * usually enough to guarantee all ports are freed when the operation returns, making it possible
 * to start another server associated to the same address.
 * </p>
 *<p>
 * If that is not the case, we say the worker is external to the server, provided to the Builder via
 * {@link io.undertow.Undertow.Builder#setWorker(XnioWorker)}. In this case, there is no way to
 * wait on the sockets closing when stopping the server, and in a test environment this means that
 * a series of opening/closing servers could lead to a {@link BindException} if a starting server tries
 * to bind to the ports used by a closing server before its address is released.
 * </p>
 * <p>To prevent that error, we need to perform some extra actions, such as {@link
 * StopServerWithExternalWorkerUtils#stopServerAndWorker(Undertow) closing } both the server
 * and the worker whenever possible, or  {@link
 * StopServerWithExternalWorkerUtils#waitWorkerRunnableCycle(XnioWorker) waiting} for a full cycle
 * of tasks to be executed in the {@link XnioWorker} plus a short period of sleep to avoid the time
 * window in which the closing server is still bound to the socket address.
 * </p>
 *
 * @see io.undertow.Undertow.Builder#setWorker(XnioWorker)
 * @see #stopServerAndWorker(Undertow)
 * @see #stopServer(Undertow)
 *
 * @author Flavia Rainone
 */
public class StopServerWithExternalWorkerUtils {

    private StopServerWithExternalWorkerUtils() {}

    /**
     * Stops the server and the external worker associated with it. Blocks until
     * the worker has fully stopped.
     *
     * @param server the Undertow server
     */
    public static void stopServerAndWorker(Undertow server) {
        final XnioWorker worker = server.getWorker();
        server.stop();
        stopWorker(worker);
    }

    /**
     * Stops the worker and waits until it is shutdown. This operation is not
     * asynchronous and will block until the worker has fully stopped.
     *
     * @param worker the XnioWorker
     */
    public static void stopWorker(XnioWorker worker) {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                List<Runnable> tasks = worker.shutdownNow();
                for (Runnable task: tasks)
                    task.run();
                if (!worker.awaitTermination(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("Worker failed to shutdown within ten seconds");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Stops only the server, keeping its external worker up and unchanged. After the server
     * is stopped, waits for a full worker runnable cycle to complete, plus a sleep time, to
     * prevent the time window in which the closing server is still bound to the associated
     * address.
     * This operation blocks until the worker finishes executing an empty Runnable task.
     *
     * @param server the Undertow server
     */
    public static void stopServer(Undertow server) {
        final XnioWorker worker = server.getWorker();
        server.stop();
        waitWorkerRunnableCycle(worker);
    }

    /**
     * Waits for a full worker runnable cycle to complete, plus a sleep time, to prevent the
     * time window in which any closing server could be still bound to its associated address.
     * This operation blocks until the worker finishes executing an empty Runnable task.
     *
     * @param worker the XnioWorker
     */
    public static void waitWorkerRunnableCycle(XnioWorker worker) {
        CountDownLatch serverShutdownLatch = new CountDownLatch(1);
        worker.getIoThread().execute(serverShutdownLatch::countDown);
        //some environments seem to need a small delay to re-bind the socket
        try {
            serverShutdownLatch.await();
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            //ignore
        }
    }
}
