package io.undertow.server.handlers.jdbclog;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class DefaultJDBCLogReceiver implements JDBCLogReceiver, Runnable, Closeable {

    private final Executor logWriteExecutor;

    private final Deque<String> pendingMessages;

    //0 = not running
    //1 = queued
    //2 = running
    @SuppressWarnings("unused")
    private volatile int state = 0;

    private static final AtomicIntegerFieldUpdater<DefaultJDBCLogReceiver> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultJDBCLogReceiver.class, "state");

    public DefaultJDBCLogReceiver(final Executor logWriteExecutor, final File outputDirectory, final String logBaseName) {
        this.logWriteExecutor = logWriteExecutor;
        this.pendingMessages = new ConcurrentLinkedDeque<String>();
    }

    @Override
    public void logMessage(String message) {
        this.pendingMessages.add(message);
        int state = stateUpdater.get(this);
        if (state == 0) {
            if (stateUpdater.compareAndSet(this, 0, 1)) {
                logWriteExecutor.execute(this);
            }
        }
    }

    @Override
    public void run() {
        if (!stateUpdater.compareAndSet(this, 1, 2)) {
            return;
        }
        List<String> messages = new ArrayList<String>();
        String msg = null;
        //only grab at most 20 messages at a time
        for (int i = 0; i < 20; ++i) {
            msg = pendingMessages.poll();
            if (msg == null) {
                break;
            }
            messages.add(msg);
        }
        if(!messages.isEmpty()) {
            writeMessage(messages);
        }
        stateUpdater.set(this, 0);
        //check to see if there is still more messages
        //if so then run this again
        if (!pendingMessages.isEmpty()) {
            if (stateUpdater.compareAndSet(this, 0, 1)) {
                logWriteExecutor.execute(this);
            }
        }
    }

    /**
     * For tests only. Blocks the current thread until all messages are written
     * Just does a busy wait.
     *
     * DO NOT USE THIS OUTSIDE OF A TEST
     */
    void awaitWrittenForTest() throws InterruptedException {
        while (!pendingMessages.isEmpty()) {
            Thread.sleep(10);
        }
        while (state != 0) {
            Thread.sleep(10);
        }
    }

    private void writeMessage(final List<String> messages) {
        for (String message : messages) {
            System.out.println("Mensagem: "+message);
        }
//        try {
//            if (writer == null) {
//                writer = new BufferedWriter(new FileWriter(defaultLogFile));
//            }
//            for (String message : messsages) {
//                writer.write(message);
//                writer.write('\n');
//            }
//            writer.flush();
//        } catch (IOException e) {
//            UndertowLogger.ROOT_LOGGER.errorWritingAccessLog(e);
//        }
    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub
    }

}
