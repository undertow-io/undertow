package io.undertow.server;

import java.io.InputStream;
import java.io.OutputStream;


/**
 * An interface that provides the input and output streams for blocking HTTP requests.
 *
 * @author Stuart Douglas
 */
public interface BlockingHttpExchange {

    InputStream getInputStream();

    OutputStream getOutputStream();

}
