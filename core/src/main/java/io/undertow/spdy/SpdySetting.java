package io.undertow.spdy;

/**
 * A Spdy Setting
 *
 * @author Stuart Douglas
 */
public class SpdySetting {

    public static final int FLAG_SETTINGS_PERSIST_VALUE = 0x1;
    public static final int FLAG_SETTINGS_PERSISTED = 0x2;

    public static final int SETTINGS_UPLOAD_BANDWIDTH = 1;
    public static final int SETTINGS_DOWNLOAD_BANDWIDTH = 2;
    public static final int SETTINGS_ROUND_TRIP_TIME = 3;
    public static final int SETTINGS_MAX_CONCURRENT_STREAMS = 4;
    public static final int SETTINGS_CURRENT_CWND = 5;
    public static final int SETTINGS_DOWNLOAD_RETRANS_RATE = 6;
    public static final int SETTINGS_INITIAL_WINDOW_SIZE = 7;
    public static final int SETTINGS_CLIENT_CERTIFICATE_VECTOR_SIZE = 8;

    private final int flags;
    private final int id;
    private final int value;

    SpdySetting(int flags, int id, int value) {
        this.flags = flags;
        this.id = id;
        this.value = value;
    }

    public int getFlags() {
        return flags;
    }

    public int getId() {
        return id;
    }

    public int getValue() {
        return value;
    }
}
