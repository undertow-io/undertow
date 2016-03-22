/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.client.http;

import io.undertow.util.HttpString;

/**
 * @author Emanuel Muckenhuber
 */
class ResponseParseState {

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
    final StringBuilder stringBuilder = new StringBuilder();

    /**
     * This has different meanings depending on the current state.
     * <p/>
     * In state {@link #HEADER} it is a the first character of the header, that was read by
     * {@link #HEADER_VALUE} to see if this was a continuation.
     * <p/>
     * In state {@link #HEADER_VALUE} if represents the last character that was seen.
     */
    byte leftOver;

    /**
     * This is used to store the next header value when parsing header key / value pairs,
     */
    HttpString nextHeader;

    ResponseParseState() {
        this.parseState = 0;
        this.pos = 0;
    }

    public boolean isComplete() {
        return state == PARSE_COMPLETE;
    }

    public final void parseComplete() {
        state = PARSE_COMPLETE;
    }
}

