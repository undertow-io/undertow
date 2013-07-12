package io.undertow.websockets.core;

/**
 * @author Stuart Douglas
 */
public class InvalidOpCodeException extends WebSocketException {

    public InvalidOpCodeException() {
    }

    public InvalidOpCodeException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public InvalidOpCodeException(String msg) {
        super(msg);
    }

    public InvalidOpCodeException(Throwable cause) {
        super(cause);
    }
}
