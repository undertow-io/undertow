package io.undertow.util;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import org.xnio.IoUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * File system watcher service.
 * <p/>
 * This services creates a managed Thread that watches some given filesystems for changes. In general only one instance of
 * this class should be created, to avoid the creation of an excessive number of threads.
 * <p/>
 * This class does not support the registration of multiple callbacks under the same file system tree.
 * <p/>
 * If JDK7 is present it will use the JDK 7 file system notification API, otherwise it falls back to polling. The poll
 * interval can be set using {@link #setPollInterval(int)}.
 *
 * @author Stuart Douglas
 */
public class FileSystemWatcher {

    /**
     * default poll time for JDK6
     */
    private static final int DEFAULT_POLL_INTERVAL = 1000;

    /**
     * atomic integer used for generating unique thread names
     */
    private static final AtomicInteger threadCount = new AtomicInteger();

    /**
     * the current state
     */
    private boolean started = false;

    /**
     * The watcher task
     */
    private FileSystemWatcherTask fileSystemWatcherTask;

    /**
     * if we should force the use of the poll watcher
     * used for testing
     */
    private boolean forcePoll = false;

    /**
     * The poll interval in milliseconds. Only applicable when using
     */
    private int pollInterval = DEFAULT_POLL_INTERVAL;

    private Thread runningThread;

    public synchronized void start() {
        if (started) {
            throw UndertowMessages.MESSAGES.fileSystemWatcherAlreadyStarted();
        }
        started = true;
        runningThread = new Thread(fileSystemWatcherTask = new FileSystemWatcherTask(), "Undertow-file-system-watcher-task-" + threadCount.incrementAndGet());
        runningThread.start();
    }

    public synchronized void stop() {
        if (!started) {
            throw UndertowMessages.MESSAGES.fileSystemWatcherNotStarted();
        }
        started = false;
        runningThread.interrupt();
        fileSystemWatcherTask.stopped = true;
        fileSystemWatcherTask = null;
        runningThread = null;
    }

    public synchronized void addPath(final File file, final FileChangeCallback callback) {
        if (!started) {
            throw UndertowMessages.MESSAGES.fileSystemWatcherNotStarted();
        }
        fileSystemWatcherTask.addPath(file, callback);
    }

    public synchronized void removePath(final File file) {
        if (!started) {
            throw UndertowMessages.MESSAGES.fileSystemWatcherNotStarted();
        }
        fileSystemWatcherTask.removePath(file);
    }

    private class FileSystemWatcherTask implements Runnable {

        volatile boolean stopped = false;
        private final Watcher watcher;


        private FileSystemWatcherTask() {
            Watcher watcher;
            if (forcePoll) {
                watcher = new PollBasedWatcher();
            } else {
                try {
                    //check if the watch service is present (i.e. are we on JDK7)
                    FileSystemWatcher.class.getClassLoader().loadClass("java.nio.file.WatchService");
                    watcher = new WatchServiceWatcher();
                } catch (ClassNotFoundException e) {
                    watcher = new PollBasedWatcher();
                }
            }
            this.watcher = watcher;
        }

        @Override
        public void run() {
            try {
                while (!stopped) {
                    try {
                        watcher.watch();
                    } catch (Exception e) {
                        //we only log this at debug
                        //as it will probably flood the log otherwise
                        //TODO: should we just exit here?
                        UndertowLogger.ROOT_LOGGER.debug("File system change detection failed", e);
                    }
                }
            } finally {
                watcher.shutdown();
            }
        }

        public void addPath(File file, FileChangeCallback callback) {
            watcher.addPath(file, callback);
        }

        public void removePath(File file) {
            watcher.removePath(file);
        }
    }

    public boolean isForcePoll() {
        return forcePoll;
    }

    public void setForcePoll(boolean forcePoll) {
        this.forcePoll = forcePoll;
    }

    public int getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(int pollInterval) {
        this.pollInterval = pollInterval;
    }

    interface Watcher {

        void watch() throws InterruptedException;

        void addPath(final File file, final FileChangeCallback contextKey);

        void removePath(final File file);

        void shutdown();
    }

    private class WatchServiceWatcher implements Watcher {

        private WatchService watchService;
        final Map<Path, WatcherHolder> files = Collections.synchronizedMap(new HashMap<Path, WatcherHolder>());

        private WatchServiceWatcher() {
            try {
                watchService = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void watch() throws InterruptedException {
            while (started && Thread.currentThread() == runningThread) {
                WatchKey key = watchService.take();
                if (key != null) {
                    try {
                        WatcherHolder holder = files.get(key.watchable());
                        if (holder != null) {
                            final List<FileChangeEvent> results = new ArrayList<FileChangeEvent>();
                            List<WatchEvent<?>> events = key.pollEvents();
                            final Set<File> addedFiles = new HashSet<File>();
                            final Set<File> deletedFiles = new HashSet<File>();
                            for (WatchEvent<?> event : events) {
                                Path eventPath = (Path) event.context();
                                File targetFile = holder.path.resolve(eventPath).toFile();
                                FileChangeEvent.Type type;

                                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                                    type = FileChangeEvent.Type.ADDED;
                                    addedFiles.add(targetFile);
                                    if (targetFile.isDirectory()) {
                                        try {
                                            addWatchedDirectory(holder.callback, holder.keys, targetFile);
                                        } catch (IOException e) {
                                            UndertowLogger.ROOT_LOGGER.debugf(e, "Could not add watched directory %s", targetFile);
                                        }
                                    }
                                } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                                    type = FileChangeEvent.Type.MODIFIED;
                                } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                    type = FileChangeEvent.Type.REMOVED;
                                    deletedFiles.add(targetFile);
                                } else {
                                    continue;
                                }
                                results.add(new FileChangeEvent(targetFile, type));
                            }
                            key.pollEvents().clear();

                            //now we need to prune the results, to remove duplicates
                            //e.g. if the file is modified after creation we only want to
                            //show the create event
                            Iterator<FileChangeEvent> it = results.iterator();
                            while (it.hasNext()) {
                                FileChangeEvent event = it.next();
                                if (event.getType() == FileChangeEvent.Type.MODIFIED) {
                                    if (addedFiles.contains(event.getFile()) ||
                                            deletedFiles.contains(event.getFile())) {
                                        it.remove();
                                    }
                                } else if (event.getType() == FileChangeEvent.Type.ADDED) {
                                    if (deletedFiles.contains(event.getFile())) {
                                        it.remove();
                                    }
                                } else if (event.getType() == FileChangeEvent.Type.REMOVED) {
                                    if (addedFiles.contains(event.getFile())) {
                                        it.remove();
                                    }
                                }
                            }

                            if (!results.isEmpty()) {
                                holder.callback.handleChanges(results);
                            }
                        }
                    } finally {
                        //if the key is no longer valid remove it from the files list
                        if (!key.reset()) {
                            files.remove(key.watchable());
                        }
                    }
                }

            }
        }

        @Override
        public synchronized void addPath(File file, FileChangeCallback contextKey) {
            try {

                //all the holders share a keylist
                //so they can all be cancelled by iterating the list
                List<WatchKey> keys = new ArrayList<WatchKey>();

                Set<File> allDirectories = doScan(file, true).keySet();
                for (File dir : allDirectories) {
                    addWatchedDirectory(contextKey, keys, dir);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void addWatchedDirectory(FileChangeCallback contextKey, List<WatchKey> keys, File dir) throws IOException {
            Path path = Paths.get(dir.toURI());
            final WatcherHolder holder = new WatcherHolder(path, contextKey, keys);
            files.put(path, holder);
            WatchKey key = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            holder.keys.add(key);
        }

        @Override
        public synchronized void removePath(File file) {
            Path path = Paths.get(file.toURI());
            WatcherHolder data = files.remove(path);
            if (data != null) {
                for (WatchKey key : data.keys) {
                    key.cancel();
                    files.remove(key.watchable());
                }
            }
        }

        @Override
        public void shutdown() {
            IoUtils.safeClose(watchService);
        }


        private class WatcherHolder {
            final Path path;
            final FileChangeCallback callback;
            final List<WatchKey> keys;

            private WatcherHolder(Path path, FileChangeCallback callback, List<WatchKey> keys) {
                this.path = path;
                this.callback = callback;
                this.keys = keys;
            }
        }
    }

    /**
     * Watcher that polls the file system looking for changes. Will only be used in JDK6
     */
    private class PollBasedWatcher implements Watcher {
        final Map<File, PollHolder> files = Collections.synchronizedMap(new HashMap<File, PollHolder>());

        private long nextTimeout = 0;

        @Override
        public void watch() throws InterruptedException {
            try {
                long currentTime = System.currentTimeMillis();
                while (Thread.currentThread() == runningThread) {
                    final long sleepTime = nextTimeout - currentTime;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                    doNotify();
                    currentTime = System.currentTimeMillis();
                    nextTimeout = currentTime + pollInterval;
                }
            } finally {
                nextTimeout = System.currentTimeMillis() + pollInterval;
            }
        }

        private void doNotify() {
            for (Map.Entry<File, PollHolder> entry : files.entrySet()) {
                Map<File, Long> result = doScan(entry.getKey(), false);
                List<FileChangeEvent> currentDiff = doDiff(result, entry.getValue().currentFileState);
                if (!currentDiff.isEmpty()) {
                    entry.getValue().currentFileState = result;
                    entry.getValue().callback.handleChanges(currentDiff);
                }
            }
        }

        private List<FileChangeEvent> doDiff(Map<File, Long> newFileState, Map<File, Long> currentFileState) {
            final List<FileChangeEvent> results = new ArrayList<FileChangeEvent>();
            final Map<File, Long> currentCopy = new HashMap<File, Long>(currentFileState);
            for (Map.Entry<File, Long> newEntry : newFileState.entrySet()) {
                Long old = currentCopy.remove(newEntry.getKey());
                if (old == null) {
                    results.add(new FileChangeEvent(newEntry.getKey(), FileChangeEvent.Type.ADDED));
                } else {
                    if (!old.equals(newEntry.getValue()) && !newEntry.getKey().isDirectory()) {
                        //we don't add modified events for directories
                        //as we will be generating modified events for the files in the dir anyway
                        results.add(new FileChangeEvent(newEntry.getKey(), FileChangeEvent.Type.MODIFIED));
                    }
                }
            }
            for (Map.Entry<File, Long> old : currentCopy.entrySet()) {
                results.add(new FileChangeEvent(old.getKey(), FileChangeEvent.Type.REMOVED));
            }
            return results;
        }

        @Override
        public void addPath(File file, final FileChangeCallback contextKey) {
            files.put(file.getAbsoluteFile(), new PollHolder(doScan(file, false), contextKey));
        }

        @Override
        public void removePath(File file) {
            files.remove(file);
        }

        @Override
        public void shutdown() {
        }

        private class PollHolder {
            Map<File, Long> currentFileState;
            final FileChangeCallback callback;


            private PollHolder(Map<File, Long> currentFileState, FileChangeCallback callback) {
                this.currentFileState = currentFileState;
                this.callback = callback;
            }
        }


    }


    private static Map<File, Long> doScan(File file, final boolean directoriesOnly) {
        final Map<File, Long> results = new HashMap<File, Long>();

        final Deque<File> toScan = new ArrayDeque<File>();
        toScan.add(file);
        while (!toScan.isEmpty()) {
            File next = toScan.pop();
            if (next.isDirectory()) {
                results.put(next, next.lastModified());
                File[] list = next.listFiles();
                if (list != null) {
                    for (File f : list) {
                        toScan.push(new File(f.getAbsolutePath()));
                    }
                }
            } else if (!directoriesOnly) {
                results.put(next, next.lastModified());
            }
        }
        return results;
    }


}
