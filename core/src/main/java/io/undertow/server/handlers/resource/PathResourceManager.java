package io.undertow.server.handlers.resource;


import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.util.ETag;
import org.jboss.logging.Logger;
import org.xnio.FileChangeCallback;
import org.xnio.FileChangeEvent;
import org.xnio.FileSystemWatcher;
import org.xnio.OptionMap;
import org.xnio.Xnio;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Serves files from the file system.
 */
public class PathResourceManager implements ResourceManager  {

    private static final Logger log = Logger.getLogger(PathResourceManager.class.getName());

    private static final boolean DEFAULT_CHANGE_LISTENERS_ALLOWED = !Boolean.getBoolean("io.undertow.disable-file-system-watcher");
    private static final long DEFAULT_TRANSFER_MIN_SIZE = 1024;
    private static final ETagFunction NULL_ETAG_FUNCTION = new ETagFunction() {
        @Override
        public ETag generate(Path path) {
            return null;
        }
    };

    private final List<ResourceChangeListener> listeners = new ArrayList<>();

    private FileSystemWatcher fileSystemWatcher;

    protected volatile String base;

    protected volatile FileSystem fileSystem;

    /**
     * Size to use direct FS to network transfer (if supported by OS/JDK) instead of read/write
     */
    private final long transferMinSize;

    /**
     * Check to validate caseSensitive issues for specific case-insensitive FS.
     * @see io.undertow.server.handlers.resource.PathResourceManager#isFileSameCase(java.nio.file.Path, String)
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

    private final ETagFunction eTagFunction;

    private final boolean allowResourceChangeListeners;

    public PathResourceManager(final Path base) {
        this(base, DEFAULT_TRANSFER_MIN_SIZE, true, false, null);
    }

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
        this(transferMinSize, caseSensitive, followLinks, DEFAULT_CHANGE_LISTENERS_ALLOWED, safePaths);
    }

    protected PathResourceManager(long transferMinSize, boolean caseSensitive, boolean followLinks, boolean allowResourceChangeListeners, final String... safePaths) {
        this.fileSystem = FileSystems.getDefault();
        this.caseSensitive = caseSensitive;
        this.followLinks = followLinks;
        this.transferMinSize = transferMinSize;
        this.allowResourceChangeListeners = allowResourceChangeListeners;
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
        this.eTagFunction = NULL_ETAG_FUNCTION;
    }

    public PathResourceManager(final Path base, long transferMinSize, boolean caseSensitive, boolean followLinks, final String... safePaths) {
        this(base, transferMinSize, caseSensitive, followLinks, DEFAULT_CHANGE_LISTENERS_ALLOWED, safePaths);
    }

    public PathResourceManager(final Path base, long transferMinSize, boolean caseSensitive, boolean followLinks, boolean allowResourceChangeListeners, final String... safePaths) {
        this(builder()
                .setBase(base)
                .setTransferMinSize(transferMinSize)
                .setCaseSensitive(caseSensitive)
                .setFollowLinks(followLinks)
                .setAllowResourceChangeListeners(allowResourceChangeListeners)
                .setSafePaths(safePaths));
    }

    private PathResourceManager(Builder builder) {
        this.allowResourceChangeListeners = builder.allowResourceChangeListeners;
        if (builder.base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        this.fileSystem = builder.base.getFileSystem();
        String basePath = null;
        try {
            if(builder.followLinks) {
                basePath = builder.base.normalize().toRealPath().toString();
            } else {
                basePath = builder.base.normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).toString();
            }
        } catch (IOException e) {
            throw UndertowMessages.MESSAGES.failedToInitializePathManager(builder.base.toString(), e);
        }
        if (!basePath.endsWith(fileSystem.getSeparator())) {
            basePath = basePath + fileSystem.getSeparator();
        }
        this.base = basePath;
        this.transferMinSize = builder.transferMinSize;
        this.caseSensitive = builder.caseSensitive;
        this.followLinks = builder.followLinks;
        if (this.followLinks) {
            if (builder.safePaths == null) {
                throw UndertowMessages.MESSAGES.argumentCannotBeNull("safePaths");
            }
            for (final String safePath : builder.safePaths) {
                if (safePath == null) {
                    throw UndertowMessages.MESSAGES.argumentCannotBeNull("safePaths");
                }
            }
            this.safePaths.addAll(Arrays.asList(builder.safePaths));
        }
        this.eTagFunction = builder.eTagFunction;
    }

    public Path getBasePath() {
        return fileSystem.getPath(base);
    }

    public PathResourceManager setBase(final Path base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        this.fileSystem = base.getFileSystem();
        String basePath = base.toAbsolutePath().toString();
        if (!basePath.endsWith(fileSystem.getSeparator())) {
            basePath = basePath + fileSystem.getSeparator();
        }
        this.base = basePath;
        return this;
    }

    public PathResourceManager setBase(final File base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        this.fileSystem = FileSystems.getDefault();
        String basePath = base.getAbsolutePath();
        if (!basePath.endsWith(fileSystem.getSeparator())) {
            basePath = basePath + fileSystem.getSeparator();
        }
        this.base = basePath;
        return this;
    }

    public Resource getResource(final String p) {
        if( p == null ) {
            return null;
        }
        String path;
        //base always ends with a /
        if (p.startsWith("/")) {
            path = p.substring(1);
        } else {
            path = p;
        }
        try {
            Path file = fileSystem.getPath(base, path);
            String normalizedFile = file.normalize().toString();
            if(!normalizedFile.startsWith(base)) {
                if(normalizedFile.length() == base.length() - 1) {
                    //special case for the root path, which may not have a trailing slash
                    if(!base.startsWith(normalizedFile)) {
                        log.tracef("Failed to get path resource %s from path resource manager with base %s, as file was outside the base directory", p, base);
                        return null;
                    }
                } else {
                    log.tracef("Failed to get path resource %s from path resource manager with base %s, as file was outside the base directory", p, base);
                    return null;
                }
            }
            if (Files.exists(file)) {
                if(path.endsWith("/") && ! Files.isDirectory(file)) {
                    //UNDERTOW-432 don't return non directories if the path ends with a /
                    log.tracef("Failed to get path resource %s from path resource manager with base %s, as path ended with a / but was not a directory", p, base);
                    return null;
                }
                boolean followAll = this.followLinks && safePaths.isEmpty();
                SymlinkResult symlinkBase = getSymlinkBase(base, file);
                if (!followAll && symlinkBase != null && symlinkBase.requiresCheck) {
                    if (this.followLinks && isSymlinkSafe(file)) {
                        return getFileResource(file, path, symlinkBase.path, normalizedFile);
                    } else {
                        log.tracef("Failed to get path resource %s from path resource manager with base %s, as it was not a safe symlink path", p, base);
                        return null;
                    }
                } else {
                    return getFileResource(file, path, symlinkBase == null ? null : symlinkBase.path, normalizedFile);
                }
            } else {
                log.tracef("Failed to get path resource %s from path resource manager with base %s, as the path did not exist", p, base);
                return null;
            }
        } catch (IOException e) {
            UndertowLogger.REQUEST_LOGGER.debugf(e, "Invalid path %s", p);
            return null;
        } catch (SecurityException e) {
            UndertowLogger.REQUEST_LOGGER.errorf(e, "Missing JSM permissions for path %s", p);
            throw e;
        } catch (Exception e) {
            UndertowLogger.REQUEST_LOGGER.debugf(e, "Other issue for path %s", p);
            return null;
        }
    }

    @Override
    public boolean isResourceChangeListenerSupported() {
        return allowResourceChangeListeners;
    }

    @Override
    public synchronized void registerResourceChangeListener(ResourceChangeListener listener) {
        if(!allowResourceChangeListeners) {
            //by rights we should throw an exception here, but this works around a bug in Wildfly where it just assumes
            //PathResourceManager supports this. This will be fixed in a later version
            return;
        }
        if (!fileSystem.equals(FileSystems.getDefault())) {
            throw new IllegalStateException("Resource change listeners not supported when using a non-default file system");
        }
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
                                if (File.separatorChar == '\\' && path.contains(File.separator)) {
                                    path = path.replace(File.separatorChar, '/');
                                }
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
        if(!allowResourceChangeListeners) {
            return;
        }
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
    private SymlinkResult getSymlinkBase(final String base, final Path file) {
        int nameCount = file.getNameCount();
        Path root = fileSystem.getPath(base);
        int rootCount = root.getNameCount();
        Path f = file;
        for (int i = nameCount - 1; i>=0; i--) {
            if (SecurityActions.isSymbolicLink(f)) {
                return new SymlinkResult(i+1 > rootCount, f);
            }
            f = f.getParent();
        }

        return null;
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
    private boolean isFileSameCase(final Path file, String normalizeFile) throws IOException {
        String canonicalName = file.toRealPath().toString();
        return canonicalName.equals(normalizeFile);
    }

    /**
     * Security check for followSymlinks feature.
     * Only follows those symbolink links defined in safePaths.
     */
    private boolean isSymlinkSafe(final Path file) throws IOException {
        String canonicalPath = file.toRealPath().toString();
        for (String safePath : this.safePaths) {
            if (safePath.length() > 0) {
                if (safePath.startsWith(fileSystem.getSeparator())) {
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
                    String absSafePath = base + fileSystem.getSeparator() + safePath;
                    Path absSafePathFile = fileSystem.getPath(absSafePath);
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
    protected PathResource getFileResource(final Path file, final String path, final Path symlinkBase, String normalizedFile) throws IOException {
        if (this.caseSensitive) {
            if (symlinkBase != null) {
                String relative = symlinkBase.relativize(file.normalize()).toString();
                String fileResolved = file.toRealPath().toString();
                String symlinkBaseResolved = symlinkBase.toRealPath().toString();
                if (!fileResolved.startsWith(symlinkBaseResolved)) {
                    log.tracef("Rejected path resource %s from path resource manager with base %s, as the case did not match actual case of %s", path, base, normalizedFile);
                    return null;
                }
                String compare = fileResolved.substring(symlinkBaseResolved.length());
                if(compare.startsWith(fileSystem.getSeparator())) {
                    compare = compare.substring(fileSystem.getSeparator().length());
                }
                if(relative.startsWith(fileSystem.getSeparator())) {
                    relative = relative.substring(fileSystem.getSeparator().length());
                }
                if (relative.equals(compare)) {
                    log.tracef("Found path resource %s from path resource manager with base %s", path, base);
                    return new PathResource(file, this, path, eTagFunction.generate(file));
                }
                log.tracef("Rejected path resource %s from path resource manager with base %s, as the case did not match actual case of %s", path, base, normalizedFile);
                return null;
            } else if (isFileSameCase(file, normalizedFile)) {
                log.tracef("Found path resource %s from path resource manager with base %s", path, base);
                return new PathResource(file, this, path, eTagFunction.generate(file));
            } else {
                log.tracef("Rejected path resource %s from path resource manager with base %s, as the case did not match actual case of %s", path, base, normalizedFile);
                return null;
            }
        } else {
            log.tracef("Found path resource %s from path resource manager with base %s", path, base);
            return new PathResource(file, this, path, eTagFunction.generate(file));
        }
    }

    private static class SymlinkResult {
        public final boolean requiresCheck;
        public final Path path;

        private SymlinkResult(boolean requiresCheck, Path path) {
            this.requiresCheck = requiresCheck;
            this.path = path;
        }
    }

    public interface ETagFunction {

        /**
         * Generates an {@link ETag} for the provided {@link Path}.
         *
         * @param path Path for which to generate an ETag
         * @return ETag representing the provided path, or null
         */
        ETag generate(Path path);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Path base;
        private long transferMinSize = DEFAULT_TRANSFER_MIN_SIZE;
        private boolean caseSensitive = true;
        private boolean followLinks = false;
        private boolean allowResourceChangeListeners = DEFAULT_CHANGE_LISTENERS_ALLOWED;
        private ETagFunction eTagFunction = NULL_ETAG_FUNCTION;
        private String[] safePaths;

        private Builder() {
        }

        public Builder setBase(Path base) {
            this.base = base;
            return this;
        }

        public Builder setTransferMinSize(long transferMinSize) {
            this.transferMinSize = transferMinSize;
            return this;
        }

        public Builder setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder setFollowLinks(boolean followLinks) {
            this.followLinks = followLinks;
            return this;
        }

        public Builder setAllowResourceChangeListeners(boolean allowResourceChangeListeners) {
            this.allowResourceChangeListeners = allowResourceChangeListeners;
            return this;
        }

        public Builder setETagFunction(ETagFunction eTagFunction) {
            this.eTagFunction = eTagFunction;
            return this;
        }

        public Builder setSafePaths(String[] safePaths) {
            this.safePaths = safePaths;
            return this;
        }

        public ResourceManager build() {
            return new PathResourceManager(this);
        }
    }
}
