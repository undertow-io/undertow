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

package io.undertow.server.handlers.accesslog;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;

/**
 * Log Receiver that stores logs in a directory under the specified file name, and rotates them after
 * midnight.
 * <p>
 * Web threads do not touch the log file, but simply queue messages to be written later by a worker thread.
 * A lightweight CAS based locking mechanism is used to ensure than only 1 thread is active writing messages at
 * any given time
 *
 * @author Stuart Douglas
 */
public class DefaultAccessLogReceiver implements AccessLogReceiver, Runnable, Closeable {
    private static final String DEFAULT_LOG_SUFFIX = "log";
    private static final int DEFAULT_RETRY_COUNT = 150;
    private static final int DEFAULT_RETRY_DELAY = 200;
    public static final String DEFAULT_RETRY_COUNT_PROPERTY = "io.undertow.accesslog.logreceiver.retryCount";
    public static final String DEFAULT_RETRY_DELAY_PROPERTY = "io.undertow.accesslog.logreceiver.retryDelay";

    private final Executor logWriteExecutor;

    private final Deque<String> pendingMessages;

    //0 = not running - access log handler is not running, nor scheduled
    //1 = queued - log handler has been scheduled to run
    //2 = running - log handler is in run() method and performs I/O
    //3 = closing/closed - run() method as triggered by close() to terminate
    @SuppressWarnings("unused")
    private volatile int state = 0;

    private static final AtomicIntegerFieldUpdater<DefaultAccessLogReceiver> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultAccessLogReceiver.class, "state");

    private long changeOverPoint;
    private String currentDateString;
    private boolean forceLogRotation;

    private final Path outputDirectory;
    private final Path defaultLogFile;

    private final String logBaseName;
    private final String logNameSuffix;

    private BufferedWriter writer = null;

    private volatile boolean closed = false;
    private boolean initialRun = true;
    private final boolean rotate;
    private final LogFileHeaderGenerator fileHeaderGenerator;
    private final int closeRetryCount;
    private final int closeRetryDelay;

    public DefaultAccessLogReceiver(final Executor logWriteExecutor, final File outputDirectory, final String logBaseName) {
        this(logWriteExecutor, outputDirectory.toPath(), logBaseName, null);
    }

    public DefaultAccessLogReceiver(final Executor logWriteExecutor, final File outputDirectory, final String logBaseName, final String logNameSuffix) {
        this(logWriteExecutor, outputDirectory.toPath(), logBaseName, logNameSuffix, true);
    }

    public DefaultAccessLogReceiver(final Executor logWriteExecutor, final File outputDirectory, final String logBaseName, final String logNameSuffix, boolean rotate) {
        this(logWriteExecutor, outputDirectory.toPath(), logBaseName, logNameSuffix, rotate);
    }

    public DefaultAccessLogReceiver(final Executor logWriteExecutor, final Path outputDirectory, final String logBaseName) {
        this(logWriteExecutor, outputDirectory, logBaseName, null);
    }

    public DefaultAccessLogReceiver(final Executor logWriteExecutor, final Path outputDirectory, final String logBaseName, final String logNameSuffix) {
        this(logWriteExecutor, outputDirectory, logBaseName, logNameSuffix, true);
    }

    public DefaultAccessLogReceiver(final Executor logWriteExecutor, final Path outputDirectory, final String logBaseName, final String logNameSuffix, boolean rotate) {
        this(logWriteExecutor, outputDirectory, logBaseName, logNameSuffix, rotate, null);
    }

    @SuppressWarnings({ "removal", "deprecation" })
    public DefaultAccessLogReceiver(final Executor logWriteExecutor, final Path outputDirectory, final String logBaseName, final String logNameSuffix, boolean rotate, LogFileHeaderGenerator fileHeader) {
        this.logWriteExecutor = logWriteExecutor;
        this.outputDirectory = outputDirectory;
        this.logBaseName = logBaseName;
        this.rotate = rotate;
        this.fileHeaderGenerator = fileHeader;
        this.logNameSuffix = (logNameSuffix != null) ? logNameSuffix : DEFAULT_LOG_SUFFIX;
        this.pendingMessages = new ConcurrentLinkedDeque<>();
        this.defaultLogFile = outputDirectory.resolve(logBaseName + this.logNameSuffix);
        calculateChangeOverPoint();

        String property = System.getSecurityManager() == null ? System.getProperty(DEFAULT_RETRY_COUNT_PROPERTY)
                : AccessController.doPrivileged(new PrivilegedAction<String>() {
                    @Override
                    public String run() {
                        return System.getProperty(DEFAULT_RETRY_COUNT_PROPERTY);
                    }
                });
        this.closeRetryCount = property == null ? DEFAULT_RETRY_COUNT : Integer.parseInt(property);

        property = System.getSecurityManager() == null ? System.getProperty(DEFAULT_RETRY_DELAY_PROPERTY)
                : AccessController.doPrivileged(new PrivilegedAction<String>() {
                    @Override
                    public String run() {
                        return System.getProperty(DEFAULT_RETRY_DELAY_PROPERTY);
                    }
                });
        this.closeRetryDelay = property == null ? DEFAULT_RETRY_DELAY : Integer.parseInt(property);
    }

    private void calculateChangeOverPoint() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.add(Calendar.DATE, 1);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        currentDateString = df.format(new Date());
        // if there is an existing default log file, use the date last modified instead of the current date
        if (Files.exists(defaultLogFile)) {
            try {
                currentDateString = df.format(new Date(Files.getLastModifiedTime(defaultLogFile).toMillis()));
            } catch(IOException e){
                // ignore. use the current date if exception happens.
            }
        }
        changeOverPoint = calendar.getTimeInMillis();
    }

    @Override
    public void logMessage(final String message) {
        if(closed) {
            //Log handler is closing, other resources should as well, there shouldn't
            //be resources served that required this to log stuff into AL file.
            throw UndertowMessages.MESSAGES.failedToLogAccessOnClose();
        }
        this.pendingMessages.add(message);
        if (this.state == 0 && stateUpdater.compareAndSet(this, 0, 1)) {
            logWriteExecutor.execute(this);
        }
    }

    /**
     * processes all queued log messages
     */
    @Override
    public void run() {
        //check if we can transition to 2. If so, perform tasks in small chunks and check this.closed.
        //move into 3 if(this.closed) and terminate run()
        if (!stateUpdater.compareAndSet(this, 1, 2)) {
            return;
        }
        //NOTE: once we are here, run() control state transition, unless it is too slow
        //and close takes over after grace period.

        if (forceLogRotation || System.currentTimeMillis() > changeOverPoint) {
            performFileRotation();
        } else if (initialRun && Files.exists(defaultLogFile)) {
            checkAndRotateOnInitialRun();
        }

        if(closed) {
            //better to check initially, rather than in loop, reach out to RAM all the time
            if (!stateUpdater.compareAndSet(this, 2, 3)) {
                UndertowLogger.ROOT_LOGGER.accessLogWorkerFailureOnTransition();
            }
            return;
        }
        //only grab at most 1000 messages at a time
        try {
            if(initOutput()) {
                for (int i = 0; i < 1000 && !pendingMessages.isEmpty(); ++i) {
                    final String msg = pendingMessages.peek();
                    if (msg == null) {
                        break;
                    }
                    if (!writeMessage(msg)) {
                        break;
                    }

                    // NOTE:this is very similar to remove(), but without screenNull
                    // at best, it will work like poll/remove, at worst, will do nothing
                    if (!pendingMessages.remove(msg)) {
                        break;
                    }
                }
            }
        }finally {
            // flush what we might have
            try {
                //this can happen when log has been rotated and there were no write
                final BufferedWriter bw = this.writer;
                if(bw != null)
                    bw.flush();
            } catch (IOException e) {
                UndertowLogger.ROOT_LOGGER.errorWritingAccessLog(e);
            }
            if(this.closed) {
                if (!stateUpdater.compareAndSet(this, 2, 3)) {
                    UndertowLogger.ROOT_LOGGER.accessLogWorkerFailureOnTransition();
                }
                return;
            } else {
                if (!pendingMessages.isEmpty() || forceLogRotation) {
                    if (stateUpdater.compareAndSet(this, 2, 1)) {
                        logWriteExecutor.execute(this);
                    } else {
                        UndertowLogger.ROOT_LOGGER.accessLogWorkerFailureOnReschedule();
                    }
                } else {
                    if (!stateUpdater.compareAndSet(this, 2, 0)) {
                        UndertowLogger.ROOT_LOGGER.accessLogWorkerFailureOnTransition();
                    }
                }
            }
        }
    }

    /**
     * For tests only. Blocks the current thread until all messages are written
     * Just does a busy wait.
     * <p>
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

    private boolean writeMessage(final String message) {
        //NOTE: is there a need to rotate on write?
        //if (System.currentTimeMillis() > changeOverPoint) {
        //    performFileRotation();
        //}
        try {
            final BufferedWriter bw = this.writer;
            if(bw != null){
                bw.write(message);
                bw.newLine();
                return true;
            }
            return false;
        } catch (IOException e) {
            UndertowLogger.ROOT_LOGGER.errorWritingAccessLog(e);
            return false;
        }
    }

    private boolean initOutput() {
        try {
            if (this.writer == null) {
                //TODO: does this ^^ need a isOpen check?
                this.writer = Files.newBufferedWriter(defaultLogFile, StandardCharsets.UTF_8, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                if (Files.size(defaultLogFile) == 0 && fileHeaderGenerator != null) {
                    String header = fileHeaderGenerator.generateHeader();
                    if (header != null) {
                        this.writer.write(header);
                        this.writer.newLine();
                        this.writer.flush();
                    }
                }
            }
            return true;
        } catch (IOException e) {
            UndertowLogger.ROOT_LOGGER.errorWritingAccessLog(e);
            return false;
        }
    }
    private void checkAndRotateOnInitialRun() {
      //if there is an existing log file check if it should be rotated
        long lm = 0;
        try {
            lm = Files.getLastModifiedTime(defaultLogFile).toMillis();
        } catch (IOException e) {
            UndertowLogger.ROOT_LOGGER.errorRotatingAccessLog(e);
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(changeOverPoint);
        c.add(Calendar.DATE, -1);
        if (lm <= c.getTimeInMillis()) {
            performFileRotation();
        }
        initialRun = false;
    }

    private void performFileRotation() {
        forceLogRotation = false;
        if (!rotate) {
            return;
        }
        try {
            if (this.writer != null) {
                this.writer.flush();
                this.writer.close();
                this.writer = null;
            }
            if (!Files.exists(defaultLogFile)) {
                return;
            }
            Path newFile = outputDirectory.resolve(logBaseName + currentDateString + "." + logNameSuffix);
            int count = 0;
            while (Files.exists(newFile)) {
                ++count;
                newFile = outputDirectory.resolve(logBaseName + currentDateString + "-" + count + "." + logNameSuffix);
            }
            Files.move(defaultLogFile, newFile);
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

    @SuppressWarnings("static-access")
    @Override
    public void close() throws IOException {
        synchronized (this) {
            if(this.closed) {
                return;
            }
            this.closed = true;
        }
        if (this.stateUpdater.compareAndSet(this, 0, 3)) {
            flushAndTerminate();
            return;
        } else {
            // state[1,2] - scheduled or running, attempt schedule hijack
            if (this.stateUpdater.compareAndSet(this, 1, 3)) {
                //this means this thread raced against scheduled run(). run() will exit ASAP
                //as 1->2 wont be possible, we are at 3 and this.closed == true
                flushAndTerminate();
                return;
            }
            // either failed race to 1->3 or we were in 2. We have to wait here sometime.
            // wait ~30s(by default), if situation does not clear up, try dumping stuff
            for(int i=0; i<this.closeRetryCount;i++) {
                try {
                    Thread.currentThread().sleep(this.closeRetryDelay);
                } catch (InterruptedException e) {
                    UndertowLogger.ROOT_LOGGER.closeInterrupted(e);
                    break;
                }
                if(this.stateUpdater.get(this) == 3) {
                    break;
                }
            }
            final int tempEndState = this.stateUpdater.getAndSet(this, 3);
            if(tempEndState == 2) {
                UndertowLogger.ROOT_LOGGER.accessLogWorkerNoTermination();
            }
            flushAndTerminate();
        }
    }

    protected void flushAndTerminate() {
        try {
            while (!this.pendingMessages.isEmpty()) {
                final String msg = this.pendingMessages.poll();
                // TODO: clarify this, how is this possible?
                if (msg == null) {
                    continue;
                }
                writeMessage(msg);
            }

            this.writer.flush();
            this.writer.close();
            this.writer = null;

        } catch (IOException e) {
            UndertowLogger.ROOT_LOGGER.errorWritingAccessLog(e);
        } finally {
            //NOTE: no need, it cant be reused?
            //stateUpdater.set(this, 0);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Executor logWriteExecutor;
        private Path outputDirectory;
        private String logBaseName;
        private String logNameSuffix;
        private boolean rotate;
        private LogFileHeaderGenerator logFileHeaderGenerator;

        public Executor getLogWriteExecutor() {
            return logWriteExecutor;
        }

        public Builder setLogWriteExecutor(Executor logWriteExecutor) {
            this.logWriteExecutor = logWriteExecutor;
            return this;
        }

        public Path getOutputDirectory() {
            return outputDirectory;
        }

        public Builder setOutputDirectory(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public String getLogBaseName() {
            return logBaseName;
        }

        public Builder setLogBaseName(String logBaseName) {
            this.logBaseName = logBaseName;
            return this;
        }

        public String getLogNameSuffix() {
            return logNameSuffix;
        }

        public Builder setLogNameSuffix(String logNameSuffix) {
            this.logNameSuffix = logNameSuffix;
            return this;
        }

        public boolean isRotate() {
            return rotate;
        }

        public Builder setRotate(boolean rotate) {
            this.rotate = rotate;
            return this;
        }

        public LogFileHeaderGenerator getLogFileHeaderGenerator() {
            return logFileHeaderGenerator;
        }

        public Builder setLogFileHeaderGenerator(LogFileHeaderGenerator logFileHeaderGenerator) {
            this.logFileHeaderGenerator = logFileHeaderGenerator;
            return this;
        }

        public DefaultAccessLogReceiver build() {
            return new DefaultAccessLogReceiver(logWriteExecutor, outputDirectory, logBaseName, logNameSuffix, rotate, logFileHeaderGenerator);
        }
    }
}
