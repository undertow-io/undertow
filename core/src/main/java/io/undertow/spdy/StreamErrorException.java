package io.undertow.spdy;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class StreamErrorException extends IOException {

    public static final int PROTOCOL_ERROR = 1;
    public static final int INVALID_STREAM = 2;
    public static final int REFUSED_STREAM = 3;
    public static final int UNSUPPORTED_VERSION = 4;
    public static final int CANCEL = 5;
    public static final int INTERNAL_ERROR = 6;
    public static final int FLOW_CONTROL_ERROR = 7;
    public static final int STREAM_IN_USE = 8;
    public static final int STREAM_ALREADY_CLOSED = 9;

    public static final int FRAME_TOO_LARGE = 11;

    private final int errorId;

    public StreamErrorException(int errorId) {
        this.errorId = errorId;
    }

    public int getErrorId() {
        return errorId;
    }
}
