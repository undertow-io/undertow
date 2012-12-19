package io.undertow.ajp;

import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
 class AjpParseState {

    //states
    public static final int BEGIN = 0;
    public static final int READING_MAGIC_NUMBER = 1;
    public static final int READING_DATA_SIZE = 2;
    public static final int READING_PREFIX_CODE = 3;
    public static final int READING_METHOD = 4;
    public static final int READING_PROTOCOL = 5;
    public static final int READING_REQUEST_URI = 6;
    public static final int READING_REMOTE_ADDR = 7;
    public static final int READING_REMOTE_HOST = 8;
    public static final int READING_SERVER_NAME = 9;
    public static final int READING_SERVER_PORT = 10;
    public static final int READING_IS_SSL = 11;
    public static final int READING_NUM_HEADERS = 12;
    public static final int READING_HEADERS = 13;
    public static final int READING_ATTRIBUTES = 14;
    public static final int DONE = 15;

    int state;

    //the length of the string being read
    int stringLength = -1;
    StringBuilder currentString;

    //when reading the first byte of an integer this stores the first value. It is set to -1 to signify that
    //the first byte has not been read yet.
    int currentIntegerPart = -1;

    int dataSize;

    int numHeaders = 0;

    HttpString currentHeader;

    String currentAttribute;

    public boolean isComplete() {
        return state == 15;
    }
}
