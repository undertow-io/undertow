package io.undertow.server.handlers.jdbclog;

/**
 * Interface that is used by the access log handler to send data to the log jdbc manager.
 *
 * Implementations of this interface must be thread safe.
 *
 * @author Filipe Ferraz
 */

public interface JDBCLogReceiver {

    void logMessage(final String message);

}
