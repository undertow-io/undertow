package io.undertow.util;

import io.undertow.UndertowLogger;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Executor that will continue to re-run tasks in a loop that are submitted from its own thread.
 *
 * @author Stuart Douglas
 */
public class PipeliningExecutor implements Executor {

    private final Executor executor;

    private static final ThreadLocal<LinkedList<Runnable>> THREAD_QUEUE = new ThreadLocal<LinkedList<Runnable>>();

    public PipeliningExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(final Runnable command) {
        List<Runnable> queue = THREAD_QUEUE.get();
        if (queue != null) {
            queue.add(command);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    LinkedList<Runnable> queue = THREAD_QUEUE.get();
                    if (queue == null) {
                        THREAD_QUEUE.set(queue = new LinkedList<Runnable>());
                    }
                    try {
                        command.run();
                    } catch (Throwable t) {
                        UndertowLogger.REQUEST_LOGGER.debugf(t, "Task %s failed", command);
                    }
                    Runnable runnable = queue.poll();
                    while (runnable != null) {
                        try {
                            runnable.run();
                        } catch (Throwable t) {
                            UndertowLogger.REQUEST_LOGGER.debugf(t, "Task %s failed", command);
                        }
                        runnable = queue.poll();
                    }

                }
            });
        }
    }
}
