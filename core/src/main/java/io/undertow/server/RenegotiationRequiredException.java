package io.undertow.server;

/**
 * Exception that is thrown that indicates that SSL renegotiation is required
 * in order to get a client cert.
 *
 * This will be thrown if a user attempts to retrieve a client cert and the SSL mode
 * is {@link org.xnio.SslClientAuthMode#NOT_REQUESTED}.
 *
 * @author Stuart Douglas
 */
public class RenegotiationRequiredException extends Exception {

    public RenegotiationRequiredException() {
    }

    public RenegotiationRequiredException(String message) {
        super(message);
    }

    public RenegotiationRequiredException(String message, Throwable cause) {
        super(message, cause);
    }

    public RenegotiationRequiredException(Throwable cause) {
        super(cause);
    }

    public RenegotiationRequiredException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
