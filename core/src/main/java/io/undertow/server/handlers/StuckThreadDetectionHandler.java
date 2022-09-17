/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers;

import io.undertow.UndertowLogger;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.WorkerUtils;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This valve allows to detect requests that take a long time to process, which might
 * indicate that the thread that is processing it is stuck.
 * Based on code proposed by TomLu in Bugzilla entry #50306
 *
 * @author slaurent
 *
 */
public class StuckThreadDetectionHandler implements HttpHandler {

    public static final int DEFAULT_THRESHOLD = 600;
    /**
     * Keeps count of the number of stuck threads detected
     */
    private final AtomicInteger stuckCount = new AtomicInteger(0);

    /**
     * In seconds. Default 600 (10 minutes).
     */
    private final int threshold;

    /**
     * The only references we keep to actual running Thread objects are in
     * this Map (which is automatically cleaned in invoke()s finally clause).
     * That way, Threads can be GC'ed, eventhough the Valve still thinks they
     * are stuck (caused by a long monitor interval)
     */
    private final ConcurrentHashMap<Long, MonitoredThread> activeThreads =
            new ConcurrentHashMap<>();
    /**
     *
     */
    private final Queue<CompletedStuckThread> completedStuckThreadsQueue =
            new ConcurrentLinkedQueue<>();

    private final HttpHandler next;


    private final Runnable stuckThreadTask = new Runnable() {
        @Override
        public void run() {
            long thresholdInMillis = threshold * 1000L;

            // Check monitored threads, being careful that the request might have
            // completed by the time we examine it
            for (MonitoredThread monitoredThread : activeThreads.values()) {
                long activeTime = monitoredThread.getActiveTimeInMillis();

                if (activeTime >= thresholdInMillis && monitoredThread.markAsStuckIfStillRunning()) {
                    int numStuckThreads = stuckCount.incrementAndGet();
                    notifyStuckThreadDetected(monitoredThread, activeTime, numStuckThreads);
                }
            }
            // Check if any threads previously reported as stuck, have finished.
            for (CompletedStuckThread completedStuckThread = completedStuckThreadsQueue.poll();
                 completedStuckThread != null; completedStuckThread = completedStuckThreadsQueue.poll()) {

                int numStuckThreads = stuckCount.decrementAndGet();
                notifyStuckThreadCompleted(completedStuckThread, numStuckThreads);
            }
            synchronized (StuckThreadDetectionHandler.this) {
                if(activeThreads.isEmpty()) {
                    timerKey = null;
                } else {
                    timerKey = WorkerUtils.executeAfter(((XnioIoThread)Thread.currentThread()), stuckThreadTask, 1, TimeUnit.SECONDS);
                }
            }
        }
    };

    private volatile XnioExecutor.Key timerKey;

    public StuckThreadDetectionHandler(HttpHandler next) {
        this(DEFAULT_THRESHOLD, next);
    }

    public StuckThreadDetectionHandler(int threshold, HttpHandler next) {
        this.threshold = threshold;
        this.next = next;
    }

    /**
     * @return The current threshold in seconds
     */
    public int getThreshold() {
        return threshold;
    }



    private void notifyStuckThreadDetected(MonitoredThread monitoredThread,
        long activeTime, int numStuckThreads) {
        Throwable th = new Throwable();
        th.setStackTrace(monitoredThread.getThread().getStackTrace());
        UndertowLogger.REQUEST_LOGGER.stuckThreadDetected
                (monitoredThread.getThread().getName(), monitoredThread.getThread().getId(),
                        activeTime, monitoredThread.getStartTime(), monitoredThread.getRequestUri(), threshold, numStuckThreads, th);
    }

    private void notifyStuckThreadCompleted(CompletedStuckThread thread,
            int numStuckThreads) {
        UndertowLogger.REQUEST_LOGGER.stuckThreadCompleted
                (thread.getName(), thread.getId(), thread.getTotalActiveTime(), numStuckThreads);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        // Save the thread/runnable
        // Keeping a reference to the thread object here does not prevent
        // GC'ing, as the reference is removed from the Map in the finally clause

        Long key = Thread.currentThread().getId();
        MonitoredThread monitoredThread = new MonitoredThread(Thread.currentThread(), exchange.getRequestURI() + exchange.getQueryString());
        activeThreads.put(key, monitoredThread);
        if(timerKey == null) {
            synchronized (this) {
                if(timerKey == null) {
                    timerKey = exchange.getIoThread().executeAfter(stuckThreadTask, 1, TimeUnit.SECONDS);
                }
            }
        }

        try {
            next.handleRequest(exchange);
        } finally {
            activeThreads.remove(key);
            if (monitoredThread.markAsDone() == MonitoredThreadState.STUCK) {
                completedStuckThreadsQueue.add(
                        new CompletedStuckThread(monitoredThread.getThread(),
                            monitoredThread.getActiveTimeInMillis()));
            }
        }
    }

    public long[] getStuckThreadIds() {
        List<Long> idList = new ArrayList<>();
        for (MonitoredThread monitoredThread : activeThreads.values()) {
            if (monitoredThread.isMarkedAsStuck()) {
                idList.add(Long.valueOf(monitoredThread.getThread().getId()));
            }
        }

        long[] result = new long[idList.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = idList.get(i).longValue();
        }
        return result;
    }

    private static class MonitoredThread {

        /**
         * Reference to the thread to get a stack trace from background task
         */
        private final Thread thread;
        private final String requestUri;
        private final long start;
        private final AtomicInteger state = new AtomicInteger(
            MonitoredThreadState.RUNNING.ordinal());

        MonitoredThread(Thread thread, String requestUri) {
            this.thread = thread;
            this.requestUri = requestUri;
            this.start = System.currentTimeMillis();
        }

        public Thread getThread() {
            return this.thread;
        }

        public String getRequestUri() {
            return requestUri;
        }

        public long getActiveTimeInMillis() {
            return System.currentTimeMillis() - start;
        }

        public Date getStartTime() {
            return new Date(start);
        }

        public boolean markAsStuckIfStillRunning() {
            return this.state.compareAndSet(MonitoredThreadState.RUNNING.ordinal(),
                MonitoredThreadState.STUCK.ordinal());
        }

        public MonitoredThreadState markAsDone() {
            int val = this.state.getAndSet(MonitoredThreadState.DONE.ordinal());
            return MonitoredThreadState.values()[val];
        }

        boolean isMarkedAsStuck() {
            return this.state.get() == MonitoredThreadState.STUCK.ordinal();
        }
    }

    private static class CompletedStuckThread {

        private final String threadName;
        private final long threadId;
        private final long totalActiveTime;

        CompletedStuckThread(Thread thread, long totalActiveTime) {
            this.threadName = thread.getName();
            this.threadId = thread.getId();
            this.totalActiveTime = totalActiveTime;
        }

        public String getName() {
            return this.threadName;
        }

        public long getId() {
            return this.threadId;
        }

        public long getTotalActiveTime() {
            return this.totalActiveTime;
        }
    }

    private enum MonitoredThreadState {
        RUNNING, STUCK, DONE;
    }

    public static final class Wrapper implements HandlerWrapper {

        private final int threshhold;

        public Wrapper(int threshhold) {
            this.threshhold = threshhold;
        }

        public Wrapper() {
            this.threshhold = DEFAULT_THRESHOLD;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new StuckThreadDetectionHandler(threshhold, handler);
        }
    }

    @Override
    public String toString() {
        return "stuck-thread-detector( " + threshold + " )";
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "stuck-thread-detector";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("threshhold", Integer.class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return "threshhold";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            Integer threshhold = (Integer) config.get("threshhold");
            if(threshhold == null) {
                return new Wrapper();
            } else {
                return new Wrapper(threshhold);
            }
        }
    }
}
