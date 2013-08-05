package io.undertow.server.handlers.resource;

import io.undertow.util.FileChangeCallback;
import io.undertow.util.FileChangeEvent;
import io.undertow.util.FileSystemWatcher;

import java.io.File;
import java.util.Collection;

/**
 * Utility class that can be used to automatically invalidate cached resource managers, using
 * a {@link io.undertow.util.FileSystemWatcher}
 *
 * @author Stuart Douglas
 */
public class CachedResourceInvalidator {

    private final FileSystemWatcher watcher;
    private final CachingResourceManager resourceManager;
    private final File rootPath;

    public CachedResourceInvalidator(FileSystemWatcher watcher, CachingResourceManager resourceManager, File rootPath) {
        this.watcher = watcher;
        this.resourceManager = resourceManager;
        this.rootPath = rootPath;
    }

    public void start() {
        watcher.addPath(rootPath, new FileChangeCallback() {
            @Override
            public void handleChanges(Collection<FileChangeEvent> changes) {
                int rootPathLength = rootPath.getAbsolutePath().length();
                for (FileChangeEvent change : changes) {
                    File file = change.getFile();
                    String path = file.getAbsolutePath().substring(rootPathLength);
                    resourceManager.invalidate(path);
                }

            }
        });
    }


    public void stop() {
        watcher.removePath(rootPath);
    }
}
