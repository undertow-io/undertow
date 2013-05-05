package io.undertow.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.undertow.io.Sender;


/**
 * An interface that provides the input and output streams for blocking HTTP requests.
 *
 * @author Stuart Douglas
 */
public interface BlockingHttpExchange {

    /**
     * Returns the input stream that is in use for this exchange.
     *
     * @return The input stream
     */
    InputStream getInputStream();

    /**
     * Returns the output stream that is in use for this exchange.
     *
     * In some circumstances this may not be available, such as if a writer
     * is being used for a servlet response
     *
     * @return The output stream
     */
    OutputStream getOutputStream();

    /**
     * Returns a sender based on the provided output stream
     *
     * @return A sender that uses the output stream
     */
    Sender getSender();

    /**
     * Closes both the input and output streams
     */
    void close() throws IOException;
}
