package io.undertow.server;

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
     * Returns the output stream that is in use for this exchange
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

}
