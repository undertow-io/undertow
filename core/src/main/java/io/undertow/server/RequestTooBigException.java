package io.undertow.server;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class RequestTooBigException extends IOException {

    public RequestTooBigException() {
        super();
    }

    public RequestTooBigException(String message) {
        super(message);
    }

    public RequestTooBigException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestTooBigException(Throwable cause) {
        super(cause);
    }
}
