package io.undertow.server.handlers.accesslog;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Log Receiver that stores logs in a directory under the specified file name, and rotates them after
 * midnight.
 *
 * Web threads do not touch the log file, but simply queue messages to be written later by a worker thread.
 * A lightwieght CAS based locking mechanism is used to ensure than only 1 thread is active writing messages at
 * any given time
 *
 * @author Stuart Douglas
 */
public class DefaultAccessLogReceiver implements AccessLogReceiver, Runnable {

    private final Executor logWriteExecutor;

    private final Deque<String> pendingMessages;

    //0 = not running
    //1 = queued
    //2 = running
    @SuppressWarnings("unused")
    private final int state = 0;

    private static final AtomicIntegerFieldUpdater<DefaultAccessLogReceiver> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultAccessLogReceiver.class, "state");

    public DefaultAccessLogReceiver(final Executor logWriteExecutor) {
        this.logWriteExecutor = logWriteExecutor;
        this.pendingMessages = new ConcurrentLinkedDeque<String>();
    }

    @Override
    public void logMessage(final String message) {
        this.pendingMessages.add(message);
        int state = stateUpdater.get(this);
        if(state == 0) {
            if(stateUpdater.compareAndSet(this, 0, 1)) {
                logWriteExecutor.execute(this);
            }
        }
    }

    /**
     * processes all queued log messages
     */
    @Override
    public void run() {
        if(!stateUpdater.compareAndSet(this, 1, 2)) {
            return;
        }
        List<String> messsages = new ArrayList<String>();
        String msg = null;
        for(int i = 0; i < 20; ++i) {
            msg = pendingMessages.poll();
            if(msg == null) {
                break;
            }
        }
    }
}
