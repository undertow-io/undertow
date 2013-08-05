package io.undertow.util;

import java.io.File;

/**
 * @author Stuart Douglas
 */
public class FileChangeEvent {

    private final File file;
    private final Type type;

    public FileChangeEvent(File file, Type type) {
        this.file = file;
        this.type = type;
    }

    public File getFile() {
        return file;
    }

    public Type getType() {
        return type;
    }

    public static enum Type {
        ADDED,
        REMOVED,
        MODIFIED
    }

}
