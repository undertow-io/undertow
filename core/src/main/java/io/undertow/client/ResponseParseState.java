package io.undertow.client;

import io.undertow.util.HttpString;

/**
 * @author Emanuel Muckenhuber
 */
public class ResponseParseState {

    //parsing states
    public static final int VERSION = 0;
    public static final int STATUS_CODE = 1;
    public static final int REASON_PHRASE = 2;
    public static final int AFTER_REASON_PHRASE = 3;
    public static final int HEADER = 4;
    public static final int HEADER_VALUE = 5;
    public static final int PARSE_COMPLETE = 6;

    /**
     * The actual state of request parsing
     */
    int state;

    /**
     * The current state in the tokenizer state machine.
     */
    int parseState;

    /**
     * If this state is a prefix or terminal match state this is set to the string
     * that is a candidate to be matched
     */
    HttpString current;

    /**
     * The bytes version of {@link #current}
     */
    byte[] currentBytes;

    /**
     * If this state is a prefix match state then this holds the current position in the string.
     */
    int pos;

    /**
     * If this is in {@link #NO_STATE} then this holds the current token that has been read so far.
     */
    StringBuilder stringBuilder;

    /**
     * This has different meanings depending on the current state.
     *
     * In state {@link #HEADER} it is a the first character of the header, that was read by
     * {@link #HEADER_VALUE} to see if this was a continuation.
     *
     * In state {@link #HEADER_VALUE} if represents the last character that was seen.
     */
    byte leftOver;

    /**
     * This is used to store the next header value when parsing header key / value pairs,
     */
    HttpString nextHeader;

    public ResponseParseState() {
        this.parseState = 0;
        this.pos = 0;
    }

    public boolean isComplete() {
        return state == PARSE_COMPLETE;
    }
}

