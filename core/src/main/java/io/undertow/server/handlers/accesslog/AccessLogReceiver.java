package io.undertow.server.handlers.accesslog;

/**
 * Interface that is used by the access log handler to send data to the log file manager.
 *
 * Implementations of this interface must be thread safe.
 *
 * @author Stuart Douglas
 */
public interface AccessLogReceiver {

    void logMessage(final String message);

}
