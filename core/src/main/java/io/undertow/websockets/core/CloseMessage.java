package io.undertow.websockets.core;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * A close message
 *
 * @author Stuart Douglas
 */
public class CloseMessage {

    private static final Charset utf8 = Charset.forName("UTF-8");

    private final int reason;
    private final String string;
 /*
    * For the exact meaning of the codes refer to the <a href="http://tools.ietf.org/html/rfc6455#section-7.4">WebSocket
    * RFC Section 7.4</a>.
    */
  public static final int NORMAL_CLOSURE = 1000;
   public static final int GOING_AWAY = 1001;
   public static final int WRONG_CODE = 1002;
   public static final int PROTOCOL_ERROR = 1003;
   public static final int MSG_CONTAINS_INVALID_DATA = 1007;
   public static final int MSG_VIOLATES_POLICY = 1008;
   public static final int MSG_TOO_BIG = 1009;
   public static final int MISSING_EXTENSIONS = 1010;
   public static final int UNEXPECTED_ERROR = 1011;

    public CloseMessage(final ByteBuffer buffer) {
        if(buffer.remaining() >= 2) {
            reason = (buffer.get() & 0XFF) << 8 | (buffer.get() & 0xFF);
            string = new UTF8Output(buffer).extract();
        } else {
            reason = GOING_AWAY;
            string = "";
        }
    }

    public CloseMessage(int reason, String string) {
        this.reason = reason;
        this.string = string;
    }

    public CloseMessage(final ByteBuffer[] buffers) {
        this(WebSockets.mergeBuffers(buffers));
    }

    public int getReason() {
        return reason;
    }

    public String getString() {
        return string;
    }

    public ByteBuffer toByteBuffer() {
        byte[] data = string.getBytes(utf8);
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 2);
        buffer.putShort((short)reason);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    /**
     * Return {@code true} if the provided code is a valid close status code.
     */
    public static boolean isValid(int code) {
        if (code >= 0 && code <= 999 || code >= 1004 && code <= 1006
                || code >= 1012 && code <= 2999) {
            return false;
        }
        return true;
    }
}
