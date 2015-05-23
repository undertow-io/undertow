package io.undertow.server.handlers.resource;


import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import org.xnio.FileChangeCallback;
import org.xnio.FileChangeEvent;
import org.xnio.FileSystemWatcher;
import org.xnio.OptionMap;
import org.xnio.Xnio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Serves files from the file system.
 */
public class PathResourceManager implements ResourceManager  {

    private final List<ResourceChangeListener> listeners = new ArrayList<>();

    private FileSystemWatcher fileSystemWatcher;

    protected volatile String base;

    /**
     * Size to use direct FS to network transfer (if supported by OS/JDK) instead of read/write
     */
    private final long transferMinSize;

    /**
     * Check to validate caseSensitive issues for specific case-insensitive FS.
     * @see io.undertow.server.handlers.resource.PathResourceManager#isFileSameCase(java.nio.file.Path)
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
    private final TreeSet<String> safePaths = new TreeSet<>();

    public PathResourceManager(final Path base, long transferMinSize) {
        this(base, transferMinSize, true, false, null);
    }

    public PathResourceManager(final Path base, long transferMinSize, boolean caseSensitive) {
        this(base, transferMinSize, caseSensitive, false, null);
    }

    public PathResourceManager(final Path base, long transferMinSize, boolean followLinks, final String... safePaths) {
        this(base, transferMinSize, true, followLinks, safePaths);
    }

    protected PathResourceManager(long transferMinSize, boolean caseSensitive, boolean followLinks, final String... safePaths) {
        this.caseSensitive = caseSensitive;
        this.followLinks = followLinks;
        this.transferMinSize = transferMinSize;
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

    public PathResourceManager(final Path base, long transferMinSize, boolean caseSensitive, boolean followLinks, final String... safePaths) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        String basePath = base.toAbsolutePath().toString();
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

    public Path getBasePath() {
        return Paths.get(base);
    }

    public PathResourceManager setBase(final Path base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        String basePath = base.toAbsolutePath().toString();
        if (!basePath.endsWith("/")) {
            basePath = basePath + '/';
        }
        this.base = basePath;
        return this;
    }

    public PathResourceManager setBase(final File base) {
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
        String path;
        //base always ends with a /
        if (p.startsWith("/")) {
            path = p.substring(1);
        } else {
            path = p;
        }
        try {
            Path file = Paths.get(base, path);
            if (Files.exists(file)) {
                if(path.endsWith("/") && ! Files.isDirectory(file)) {
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
                    synchronized (PathResourceManager.this) {
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
    private boolean isSymlinkPath(final String base, final Path file) throws IOException {
        int nameCount = file.getNameCount();
        Path root = Paths.get(base);
        int rootCount = root.getNameCount();
        if (nameCount > rootCount) {
            Path f = root;
            for (int i= rootCount; i<nameCount; i++) {
                f = f.resolve(file.getName(i).toString());
                if (Files.isSymbolicLink(f)) {
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
    private boolean isFileSameCase(final Path file) throws IOException {
        String canonicalName = file.toRealPath().getFileName().toString();
        if (canonicalName.equals(file.getFileName().toString())) {
            return true;
        } else {
            return !canonicalName.equalsIgnoreCase(file.getFileName().toString());
        }
    }

    /**
     * Security check for followSymlinks feature.
     * Only follows those symbolink links defined in safePaths.
     */
    private boolean isSymlinkSafe(final Path file) throws IOException {
        String canonicalPath = file.toRealPath().toString();
        for (String safePath : this.safePaths) {
            if (safePath.length() > 0) {
                if (safePath.charAt(0) == '/') {
                    /*
                     * Absolute path
                     */
                    if (safePath.length() > 0 &&
                            canonicalPath.length() >= safePath.length() &&
                            canonicalPath.startsWith(safePath)) {
                        return true;
                    }
                } else {
                    /*
                     * In relative path we build the path appending to base
                     */
                    String absSafePath = base + '/' + safePath;
                    Path absSafePathFile = Paths.get(absSafePath);
                    String canonicalSafePath = absSafePathFile.toRealPath().toString();
                    if (canonicalSafePath.length() > 0 &&
                            canonicalPath.length() >= canonicalSafePath.length() &&
                            canonicalPath.startsWith(canonicalSafePath)) {
                        return true;
                    }

                }
            }
        }
        return false;
    }

    /**
     * Apply security check for case insensitive file systems.
     */
    protected PathResource getFileResource(final Path file, final String path) throws IOException {
        if (this.caseSensitive) {
            if (isFileSameCase(file)) {
                return new PathResource(file, this, path);
            } else {
                return null;
            }
        } else {
            return new PathResource(file, this, path);
        }
    }
}
