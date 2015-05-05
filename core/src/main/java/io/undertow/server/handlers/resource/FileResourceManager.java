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

package io.undertow.server.handlers.resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import org.xnio.FileChangeCallback;
import org.xnio.FileChangeEvent;
import org.xnio.FileSystemWatcher;
import org.xnio.OptionMap;
import org.xnio.Xnio;

/**
 * Serves files from the file system.
 */
public class FileResourceManager implements ResourceManager {

    private final List<ResourceChangeListener> listeners = new ArrayList<>();

    private FileSystemWatcher fileSystemWatcher;

    private volatile String base;

    /**
     * Size to use direct FS to network transfer (if supported by OS/JDK) instead of read/write
     */
    private final long transferMinSize;

    /**
     * Check to validate caseSensitive issues for specific case-insensitive FS.
     * @see io.undertow.server.handlers.resource.FileResourceManager#isFileSameCase(java.io.File)
     */
    private final boolean caseSensitive;

    /**
     * Check to allow follow symbolic links
     */
    private final boolean followLinks;

    /**
     * Used if followLinks == true. Set of paths valid to follow symbolic links. If this is empty and followLinks
     * it true then all links will be followed
     */
    private final TreeSet<String> safePaths = new TreeSet<String>();

    public FileResourceManager(final File base, long transferMinSize) {
        this(base, transferMinSize, true, false, null);
    }

    public FileResourceManager(final File base, long transferMinSize, boolean caseSensitive) {
        this(base, transferMinSize, caseSensitive, false, null);
    }

    public FileResourceManager(final File base, long transferMinSize, boolean followLinks, final String... safePaths) {
        this(base, transferMinSize, true, followLinks, safePaths);
    }

    public FileResourceManager(final File base, long transferMinSize, boolean caseSensitive, boolean followLinks, final String... safePaths) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        String basePath = base.getAbsolutePath();
        if (!basePath.endsWith("/")) {
            basePath = basePath + '/';
        }
        this.base = basePath;
        this.transferMinSize = transferMinSize;
        this.caseSensitive = caseSensitive;
        this.followLinks = followLinks;
        if (this.followLinks) {
            if (safePaths == null) {
                throw UndertowMessages.MESSAGES.argumentCannotBeNull("safePaths");
            }
            for (final String safePath : safePaths) {
                if (safePath == null) {
                    throw UndertowMessages.MESSAGES.argumentCannotBeNull("safePaths");
                }
            }
            this.safePaths.addAll(Arrays.asList(safePaths));
        }
    }

    public File getBase() {
        return new File(base);
    }

    public FileResourceManager setBase(final File base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        String basePath = base.getAbsolutePath();
        if (!basePath.endsWith("/")) {
            basePath = basePath + '/';
        }
        this.base = basePath;
        return this;
    }

    public Resource getResource(final String p) {
        String path = null;
        //base always ends with a /
        if (p.startsWith("/")) {
            path = p.substring(1);
        } else {
            path = p;
        }
        try {
            File file = new File(base, path);
            if (file.exists()) {
                if(path.endsWith("/") && ! file.isDirectory()) {
                    //UNDERTOW-432 don't return non directories if the path ends with a /
                    return null;
                }
                boolean followAll = this.followLinks && safePaths.isEmpty();
                if (!followAll && isSymlinkPath(base, file)) {
                    if (this.followLinks && isSymlinkSafe(file)) {
                        return getFileResource(file, path);
                    }
                } else {
                    return getFileResource(file, path);
                }
            }
            return null;
        } catch (Exception e) {
            UndertowLogger.REQUEST_LOGGER.debugf(e, "Invalid path %s");
            return null;
        }
    }

    @Override
    public boolean isResourceChangeListenerSupported() {
        return true;
    }

    @Override
    public synchronized void registerResourceChangeListener(ResourceChangeListener listener) {
        listeners.add(listener);
        if (fileSystemWatcher == null) {
            fileSystemWatcher = Xnio.getInstance().createFileSystemWatcher("Watcher for " + base, OptionMap.EMPTY);
            fileSystemWatcher.watchPath(new File(base), new FileChangeCallback() {
                @Override
                public void handleChanges(Collection<FileChangeEvent> changes) {
                    synchronized (FileResourceManager.this) {
                        final List<ResourceChangeEvent> events = new ArrayList<>();
                        for (FileChangeEvent change : changes) {
                            if (change.getFile().getAbsolutePath().startsWith(base)) {
                                String path = change.getFile().getAbsolutePath().substring(base.length());
                                events.add(new ResourceChangeEvent(path, ResourceChangeEvent.Type.valueOf(change.getType().name())));
                            }
                        }
                        for (ResourceChangeListener listener : listeners) {
                            listener.handleChanges(events);
                        }
                    }
                }
            });
        }
    }


    @Override
    public synchronized void removeResourceChangeListener(ResourceChangeListener listener) {
        listeners.remove(listener);
    }

    public long getTransferMinSize() {
        return transferMinSize;
    }

    @Override
    public synchronized void close() throws IOException {
        if (fileSystemWatcher != null) {
            fileSystemWatcher.close();
        }
    }

    /**
     * Returns true is some element of path inside base path is a symlink.
     */
    private boolean isSymlinkPath(final String base, final File file) throws IOException {
        Path path = file.toPath();
        int nameCount = path.getNameCount();
        File root = new File(base);
        Path rootPath = root.toPath();
        int rootCount = rootPath.getNameCount();
        if (nameCount > rootCount) {
            File f = root;
            for (int i= rootCount; i<nameCount; i++) {
                f = new File(f, path.getName(i).toString());
                if (Files.isSymbolicLink(f.toPath())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Security check for case insensitive file systems.
     * We make sure the case of the filename matches the case of the request.
     * This is only a check for case sensitivity, not for non canonical . and ../ which are allowed.
     *
     * For example:
     * file.getName() == "page.jsp" && file.getCanonicalFile().getName() == "page.jsp" should return true
     * file.getName() == "page.jsp" && file.getCanonicalFile().getName() == "page.JSP" should return false
     * file.getName() == "./page.jsp" && file.getCanonicalFile().getName() == "page.jsp" should return true
     */
    private boolean isFileSameCase(final File file) throws IOException {
        String canonicalName = file.getCanonicalFile().getName();
        if (canonicalName.equals(file.getName())) {
            return true;
        } else {
            return !canonicalName.equalsIgnoreCase(file.getName());
        }
    }

    /**
     * Security check for followSymlinks feature.
     * Only follows those symbolink links defined in safePaths.
     */
    private boolean isSymlinkSafe(final File file) throws IOException {
        String canonicalPath = file.getCanonicalPath();
        for (String safePath : this.safePaths) {
            if (safePath.length() > 0) {
                if (safePath.charAt(0) == '/') {
                    /*
                     * Absolute path
                     */
                    return safePath.length() > 0 &&
                            canonicalPath.length() >= safePath.length() &&
                            canonicalPath.startsWith(safePath);
                } else {
                    /*
                     * In relative path we build the path appending to base
                     */
                    String absSafePath = base + '/' + safePath;
                    File absSafePathFile = new File(absSafePath);
                    String canonicalSafePath = absSafePathFile.getCanonicalPath();
                    return canonicalSafePath.length() > 0 &&
                            canonicalPath.length() >= canonicalSafePath.length() &&
                            canonicalPath.startsWith(canonicalSafePath);

                }
            }
        }
        return false;
    }

    /**
     * Apply security check for case insensitive file systems.
     */
    private FileResource getFileResource(final File file, final String path) throws IOException {
        if (this.caseSensitive) {
            if (isFileSameCase(file)) {
                return new FileResource(file, this, path);
            } else {
                return null;
            }
        } else {
            return new FileResource(file, this, path);
        }
    }
}
