package io.undertow.client.ajp;

import io.undertow.server.protocol.ajp.AbstractAjpParseState;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
class AjpResponseParseState extends AbstractAjpParseState {

    //states
    public static final int BEGIN = 0;
    public static final int READING_MAGIC_NUMBER = 1;
    public static final int READING_DATA_SIZE = 2;
    public static final int READING_PREFIX_CODE = 3;
    public static final int READING_STATUS_CODE = 4;
    public static final int READING_REASON_PHRASE = 5;
    public static final int READING_NUM_HEADERS = 6;
    public static final int READING_HEADERS = 7;
    public static final int DONE = 15;

    int state;

    byte prefix;

    int dataSize;

    int numHeaders = 0;

    HttpString currentHeader;

    public boolean isComplete() {
        return state == DONE;
    }

    public void reset() {
        super.reset();
        state = 0;
        prefix = 0;
        dataSize = 0;
        numHeaders = 0;
        currentHeader = null;
    }
}
