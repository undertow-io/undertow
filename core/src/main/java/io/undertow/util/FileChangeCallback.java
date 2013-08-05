package io.undertow.util;

import java.util.Collection;

/**
 * Notification of file system change events
 *
 * @author Stuart Douglas
 */
public interface FileChangeCallback {

    void handleChanges(final Collection<FileChangeEvent> changes);

}
