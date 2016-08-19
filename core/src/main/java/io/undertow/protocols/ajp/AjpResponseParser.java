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

package io.undertow.protocols.ajp;

import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.nio.ByteBuffer;

import static io.undertow.protocols.ajp.AjpConstants.FRAME_TYPE_END_RESPONSE;
import static io.undertow.protocols.ajp.AjpConstants.FRAME_TYPE_REQUEST_BODY_CHUNK;
import static io.undertow.protocols.ajp.AjpConstants.FRAME_TYPE_SEND_BODY_CHUNK;
import static io.undertow.protocols.ajp.AjpConstants.FRAME_TYPE_SEND_HEADERS;

/**
 * Parser used for the client (i.e. load balancer) side of the AJP connection.
 *
 * @author Stuart Douglas
 */
class AjpResponseParser {

    public static final AjpResponseParser INSTANCE = new AjpResponseParser();

    private static final int AB = ('A' << 8) + 'B';

    //states
    public static final int BEGIN = 0;
    public static final int READING_MAGIC_NUMBER = 1;
    public static final int READING_DATA_SIZE = 2;
    public static final int READING_PREFIX_CODE = 3;
    public static final int READING_STATUS_CODE = 4;
    public static final int READING_REASON_PHRASE = 5;
    public static final int READING_NUM_HEADERS = 6;
    public static final int READING_HEADERS = 7;
    public static final int READING_PERSISTENT_BOOLEAN = 8;
    public static final int READING_BODY_CHUNK_LENGTH = 9;
    public static final int DONE = 10;

    //parser states
    int state;
    byte prefix;
    int numHeaders = 0;
    HttpString currentHeader;

    //final states
    int statusCode;
    String reasonPhrase;
    HeaderMap headers = new HeaderMap();
    int readBodyChunkSize;

    public boolean isComplete() {
        return state == DONE;
    }

    public void parse(final ByteBuffer buf) throws IOException {
        if (!buf.hasRemaining()) {
            return;
        }
        switch (this.state) {
            case BEGIN: {
                IntegerHolder result = parse16BitInteger(buf);
                if (!result.readComplete) {
                    return;
                } else {
                    if (result.value != AB) {
                        throw new IOException("Wrong magic number");
                    }
                }
            }
            case READING_DATA_SIZE: {
                IntegerHolder result = parse16BitInteger(buf);
                if (!result.readComplete) {
                    this.state = READING_DATA_SIZE;
                    return;
                }
            }
            case READING_PREFIX_CODE: {
                if (!buf.hasRemaining()) {
                    this.state = READING_PREFIX_CODE;
                    return;
                } else {
                    final byte prefix = buf.get();
                    this.prefix = prefix;
                    if (prefix == FRAME_TYPE_END_RESPONSE) {
                        this.state = READING_PERSISTENT_BOOLEAN;
                        break;
                    } else if (prefix == FRAME_TYPE_SEND_BODY_CHUNK) {
                        this.state = READING_BODY_CHUNK_LENGTH;
                        break;
                    } else if (prefix != FRAME_TYPE_SEND_HEADERS && prefix != FRAME_TYPE_REQUEST_BODY_CHUNK) {
                        this.state = DONE;
                        return;
                    }
                }
            }
            case READING_STATUS_CODE: {
                //this state is overloaded for the request size
                //when reading state=6 (read_body_chunk requests)

                IntegerHolder result = parse16BitInteger(buf);
                if (result.readComplete) {
                    if (this.prefix == FRAME_TYPE_SEND_HEADERS) {
                        statusCode = result.value;
                    } else {
                        //read body chunk or end result
                        //a bit hacky
                        this.state = DONE;
                        this.readBodyChunkSize = result.value;
                        return;
                    }
                } else {
                    this.state = READING_STATUS_CODE;
                    return;
                }
            }
            case READING_REASON_PHRASE: {
                StringHolder result = parseString(buf, false);
                if (result.readComplete) {
                    reasonPhrase = result.value;
                    //exchange.setRequestURI(result.value);
                } else {
                    this.state = READING_REASON_PHRASE;
                    return;
                }
            }
            case READING_NUM_HEADERS: {
                IntegerHolder result = parse16BitInteger(buf);
                if (!result.readComplete) {
                    this.state = READING_NUM_HEADERS;
                    return;
                } else {
                    this.numHeaders = result.value;
                }
            }
            case READING_HEADERS: {
                int readHeaders = this.readHeaders;
                while (readHeaders < this.numHeaders) {
                    if (this.currentHeader == null) {
                        StringHolder result = parseString(buf, true);
                        if (!result.readComplete) {
                            this.state = READING_HEADERS;
                            this.readHeaders = readHeaders;
                            return;
                        }
                        if (result.header != null) {
                            this.currentHeader = result.header;
                        } else {
                            this.currentHeader = HttpString.tryFromString(result.value);
                        }
                    }
                    StringHolder result = parseString(buf, false);
                    if (!result.readComplete) {
                        this.state = READING_HEADERS;
                        this.readHeaders = readHeaders;
                        return;
                    }
                    headers.add(this.currentHeader, result.value);
                    this.currentHeader = null;
                    ++readHeaders;
                }
                break;
            }
        }

        if (state == READING_PERSISTENT_BOOLEAN) {
            if (!buf.hasRemaining()) {
                return;
            }
            currentIntegerPart = buf.get();
            this.state = DONE;
            return;
        } else if (state == READING_BODY_CHUNK_LENGTH) {
            IntegerHolder result = parse16BitInteger(buf);
            if (result.readComplete) {
                this.currentIntegerPart = result.value;
                this.state = DONE;
            }
            return;
        } else {
            this.state = DONE;
        }
    }

    protected HttpString headers(int offset) {
        return AjpConstants.HTTP_HEADERS_ARRAY[offset];
    }

    public HeaderMap getHeaders() {
        return headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public int getReadBodyChunkSize() {
        return readBodyChunkSize;
    }

    public static final int STRING_LENGTH_MASK = 1 << 31;

    /**
     * The length of the string being read
     */
    public int stringLength = -1;

    /**
     * The current string being read
     */
    public StringBuilder currentString;

    /**
     * when reading the first byte of an integer this stores the first value. It is set to -1 to signify that
     * the first byte has not been read yet.
     */
    public int currentIntegerPart = -1;
    boolean containsUrlCharacters = false;
    public int readHeaders = 0;

    public void reset() {

        state = 0;
        prefix = 0;
        numHeaders = 0;
        currentHeader = null;

        statusCode = 0;
        reasonPhrase = null;
        headers = new HeaderMap();
        stringLength = -1;
        currentString = null;
        currentIntegerPart = -1;
        readHeaders = 0;
    }

    protected IntegerHolder parse16BitInteger(ByteBuffer buf) {
        if (!buf.hasRemaining()) {
            return new IntegerHolder(-1, false);
        }
        int number = this.currentIntegerPart;
        if (number == -1) {
            number = (buf.get() & 0xFF);
        }
        if (buf.hasRemaining()) {
            final byte b = buf.get();
            int result = ((0xFF & number) << 8) + (b & 0xFF);
            this.currentIntegerPart = -1;
            return new IntegerHolder(result, true);
        } else {
            this.currentIntegerPart = number;
            return new IntegerHolder(-1, false);
        }
    }

    protected StringHolder parseString(ByteBuffer buf, boolean header) {
        boolean containsUrlCharacters = this.containsUrlCharacters;
        if (!buf.hasRemaining()) {
            return new StringHolder(null, false, false);
        }
        int stringLength = this.stringLength;
        if (stringLength == -1) {
            int number = buf.get() & 0xFF;
            if (buf.hasRemaining()) {
                final byte b = buf.get();
                stringLength = ((0xFF & number) << 8) + (b & 0xFF);
            } else {
                this.stringLength = number | STRING_LENGTH_MASK;
                return new StringHolder(null, false, false);
            }
        } else if ((stringLength & STRING_LENGTH_MASK) != 0) {
            int number = stringLength & ~STRING_LENGTH_MASK;
            stringLength = ((0xFF & number) << 8) + (buf.get() & 0xFF);
        }
        if (header && (stringLength & 0xFF00) != 0) {
            this.stringLength = -1;
            return new StringHolder(headers(stringLength & 0xFF));
        }
        if (stringLength == 0xFFFF) {
            //OxFFFF means null
            this.stringLength = -1;
            return new StringHolder(null, true, false);
        }
        StringBuilder builder = this.currentString;

        if (builder == null) {
            builder = new StringBuilder();
            this.currentString = builder;
        }
        int length = builder.length();
        while (length < stringLength) {
            if (!buf.hasRemaining()) {
                this.stringLength = stringLength;
                this.containsUrlCharacters = containsUrlCharacters;
                return new StringHolder(null, false, false);
            }
            char c = (char) buf.get();
            if(c == '+' || c == '%') {
                containsUrlCharacters = true;
            }
            builder.append(c);
            ++length;
        }

        if (buf.hasRemaining()) {
            buf.get(); //null terminator
            this.currentString = null;
            this.stringLength = -1;
            this.containsUrlCharacters = false;
            return new StringHolder(builder.toString(), true, containsUrlCharacters);
        } else {
            this.stringLength = stringLength;
            this.containsUrlCharacters = containsUrlCharacters;
            return new StringHolder(null, false, false);
        }
    }

    protected static class IntegerHolder {
        public final int value;
        public final boolean readComplete;

        private IntegerHolder(int value, boolean readComplete) {
            this.value = value;
            this.readComplete = readComplete;
        }
    }

    protected static class StringHolder {
        public final String value;
        public final HttpString header;
        public final boolean readComplete;
        public final boolean containsUrlCharacters;

        private StringHolder(String value, boolean readComplete, boolean containsUrlCharacters) {
            this.value = value;
            this.readComplete = readComplete;
            this.containsUrlCharacters = containsUrlCharacters;
            this.header = null;
        }

        private StringHolder(HttpString value) {
            this.value = null;
            this.readComplete = true;
            this.header = value;
            this.containsUrlCharacters = false;
        }
    }
}
