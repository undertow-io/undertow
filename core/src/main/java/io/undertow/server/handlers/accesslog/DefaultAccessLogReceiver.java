package io.undertow.server.handlers.accesslog;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.undertow.UndertowLogger;

/**
 * Log Receiver that stores logs in a directory under the specified file name, and rotates them after
 * midnight.
 * <p/>
 * Web threads do not touch the log file, but simply queue messages to be written later by a worker thread.
 * A lightwieght CAS based locking mechanism is used to ensure than only 1 thread is active writing messages at
 * any given time
 *
 * @author Stuart Douglas
 */
public class DefaultAccessLogReceiver implements AccessLogReceiver, Runnable, Closeable {

    private final Executor logWriteExecutor;

    private final Deque<String> pendingMessages;

    //0 = not running
    //1 = queued
    //2 = running
    @SuppressWarnings("unused")
    private volatile int state = 0;

    private static final AtomicIntegerFieldUpdater<DefaultAccessLogReceiver> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultAccessLogReceiver.class, "state");

    private long changeOverPoint;
    private String currentDateString;
    private boolean forceLogRotation;

    private final File outputDirectory;
    private final File defaultLogFile;

    private final String logBaseName;

    private Writer writer = null;

    public DefaultAccessLogReceiver(final Executor logWriteExecutor, final File outputDirectory, final String logBaseName) {
        this.logWriteExecutor = logWriteExecutor;
        this.outputDirectory = outputDirectory;
        this.logBaseName = logBaseName;
        this.pendingMessages = new ConcurrentLinkedDeque<String>();
        this.defaultLogFile = new File(outputDirectory, logBaseName + ".log");
        calculateChangeOverPoint();
    }

    private void calculateChangeOverPoint() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.HOUR, 23);
        changeOverPoint = calendar.getTimeInMillis();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        currentDateString = df.format(new Date());
    }

    @Override
    public void logMessage(final String message) {
        this.pendingMessages.add(message);
        int state = stateUpdater.get(this);
        if (state == 0) {
            if (stateUpdater.compareAndSet(this, 0, 1)) {
                logWriteExecutor.execute(this);
            }
        }
    }

    /**
     * processes all queued log messages
     */
    @Override
    public void run() {
        if (!stateUpdater.compareAndSet(this, 1, 2)) {
            return;
        }
        if(forceLogRotation) {
            doRotate();
        }
        List<String> messsages = new ArrayList<String>();
        String msg = null;
        //only grab at most 1000 messages at a time
        for (int i = 0; i < 1000; ++i) {
            msg = pendingMessages.poll();
            if (msg == null) {
                break;
            }
            messsages.add(msg);
        }
        if(!messsages.isEmpty()) {
            writeMessage(messsages);
        }
        stateUpdater.set(this, 0);
        //check to see if there is still more messages
        //if so then run this again
        if (!pendingMessages.isEmpty() || forceLogRotation) {
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
        while (!pendingMessages.isEmpty() || forceLogRotation) {
            Thread.sleep(10);
        }
        while (state != 0) {
            Thread.sleep(10);
        }
    }

    private void writeMessage(final List<String> messsages) {
        if (System.currentTimeMillis() > changeOverPoint) {
            doRotate();
        }
        try {
            if (writer == null) {
                writer = new BufferedWriter(new FileWriter(defaultLogFile));
            }
            for (String message : messsages) {
                writer.write(message);
                writer.write('\n');
            }
            writer.flush();
        } catch (IOException e) {
            UndertowLogger.ROOT_LOGGER.errorWritingAccessLog(e);
        }
    }

    private void doRotate() {
        forceLogRotation = false;
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
                writer = null;
            }
            File newFile = new File(outputDirectory, logBaseName + "_" + currentDateString + ".log");
            int count = 0;
            while (newFile.exists())  {
                ++count;
                newFile = new File(outputDirectory, logBaseName + "_" + currentDateString  + "-" + count + ".log");
            }
            if (!defaultLogFile.renameTo(newFile)) {
                UndertowLogger.ROOT_LOGGER.errorRotatingAccessLog(new IOException());
            }
        } catch (IOException e) {
            UndertowLogger.ROOT_LOGGER.errorRotatingAccessLog(e);
        } finally {
            calculateChangeOverPoint();
        }
    }

    /**
     * forces a log rotation. This rotation is performed in an async manner, you cannot rely on the rotation
     * being performed immediately after this method returns.
     */
    public void rotate() {
        forceLogRotation = true;
        if (stateUpdater.compareAndSet(this, 0, 1)) {
            logWriteExecutor.execute(this);
        }
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
        writer = null;
    }
}
