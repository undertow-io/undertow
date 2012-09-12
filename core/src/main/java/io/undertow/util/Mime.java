/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class Mime {

    /**
     * The Carriage Return ASCII character value.
     */
    public static final byte CR = 0x0D;


    /**
     * The Line Feed ASCII character value.
     */
    public static final byte LF = 0x0A;


    /**
     * The dash (-) ASCII character value.
     */
    public static final byte DASH = 0x2D;

    /**
     * A byte sequence that precedes a boundary (<code>CRLF--</code>).
     */
    private static final byte[] BOUNDARY_PREFIX = {CR, LF, DASH, DASH};

    public interface PartHandler {
        void beginPart(final HeaderMap headers);

        void data(final ByteBuffer buffer);

        void endPart();
    }

    public static ParseState beginParse(final PartHandler handler, final byte[] boundary) {

        // We prepend CR/LF to the boundary to chop trailing CR/LF from
        // body-data tokens.
        byte[] boundaryToken = new byte[boundary.length + BOUNDARY_PREFIX.length];
        System.arraycopy(BOUNDARY_PREFIX, 0, boundaryToken, 0, BOUNDARY_PREFIX.length);
        System.arraycopy(boundary, 0, boundaryToken, BOUNDARY_PREFIX.length, boundary.length);
        return new ParseState(handler, boundaryToken);
    }


    public static class MimeMessage {

    }

    public static class ParseState {
        private final PartHandler partHandler;
        /**
         * The boundary, complete with the initial CRLF--
         */
        private final byte[] boundary;

        //0=preamble
        private volatile int state = 0;
        private volatile int subState = Integer.MAX_VALUE; // used for preamble parsing
        private volatile StringBuilder currentString = null;
        private volatile String currentHeaderName = null;
        private volatile HeaderMap headers;


        public ParseState(final PartHandler partHandler, final byte[] boundary) {
            this.partHandler = partHandler;
            this.boundary = boundary;
        }

        public void parse(ByteBuffer buffer) throws MalformedMessageException {
            while (buffer.hasRemaining()) {
                switch (state) {
                    case 0: {
                        preamble(buffer);
                        break;
                    }
                    case 1: {
                        headerName(buffer);
                        break;
                    }
                    case 2: {
                        headerValue(buffer);
                        break;
                    }
                    case 3: {
                        identityEncoding(buffer);
                        break;
                    }
                    case -1: {
                        return;
                    }
                    default: {
                        throw new IllegalStateException("" + state);
                    }
                }
            }
        }

        private void preamble(final ByteBuffer buffer) {
            while (buffer.hasRemaining()) {
                final byte b = buffer.get();
                if (subState >= 0) {
                    //handle the case of no preamble. In this case there is no CRLF
                    if(subState == Integer.MAX_VALUE) {
                        if(boundary[2] == b) {
                            subState = 2;
                        } else {
                            subState = 0;
                        }
                    }
                    if (b == boundary[subState]) {
                        subState++;
                        if (subState == boundary.length) {
                            subState = -1;
                        }
                    } else if (b == boundary[0]) {
                        subState = 1;
                    } else {
                        subState = 0;
                    }
                } else if (subState == -1) {
                    if (b == CR) {
                        subState = -2;
                    }
                } else if (subState == -2) {
                    if (b == LF) {
                        subState = 0;
                        state = 1;//preaamble is done
                        headers = new HeaderMap();
                        return;
                    } else {
                        subState = -1;
                    }
                }
            }
        }

        private void headerName(final ByteBuffer buffer) throws MalformedMessageException {
            while (buffer.hasRemaining()) {
                final byte b = buffer.get();
                if (b == ':') {
                    if (currentString == null || subState != 0) {
                        throw new MalformedMessageException();
                    } else {
                        currentHeaderName = currentString.toString();
                        currentString.setLength(0);
                        subState = 0;
                        state = 2;
                        return;
                    }
                } else if (b == CR) {
                    if (currentString != null) {
                        throw new MalformedMessageException();
                    } else {
                        subState = 1;
                    }
                } else if (b == LF) {
                    if (currentString != null || subState != 1) {
                        throw new MalformedMessageException();
                    }
                    state = 3;
                    subState = 0;
                    partHandler.beginPart(headers);
                    //TODO: switch state based on encoding

                    headers = null;
                    return;

                } else {
                    if (subState != 0) {
                        throw new MalformedMessageException();
                    } else if (currentString == null) {
                        currentString = new StringBuilder();
                    }
                    currentString.append((char) b);
                }
            }
        }

        private void headerValue(final ByteBuffer buffer) throws MalformedMessageException {
            while (buffer.hasRemaining()) {
                final byte b = buffer.get();
                if (b == CR) {
                    subState = 1;
                } else if (b == LF) {
                    if (subState != 1) {
                        throw new MalformedMessageException();
                    }
                    headers.put(currentHeaderName.trim(), currentString.toString().trim());
                    state = 1;
                    subState = 0;
                    currentString = null;
                    return;
                } else {
                    if (subState != 0) {
                        throw new MalformedMessageException();
                    }
                    currentString.append((char) b);
                }
            }
        }

        private void identityEncoding(final ByteBuffer buffer) {
            int pos = buffer.position();
            while (buffer.hasRemaining()) {
                final byte b = buffer.get();
                if (subState >= 0) {
                    if (b == boundary[subState]) {
                        //if we have a potential boundary match
                        subState++;
                        if (subState == boundary.length) {
                            //we have our data
                            ByteBuffer retBuffer = buffer.duplicate();
                            retBuffer.position(pos);
                            retBuffer.limit(buffer.position() - boundary.length);
                            partHandler.data(retBuffer);
                            subState = -1;
                        }
                    } else if (b == boundary[0]) {
                        subState = 1;
                    } else {
                        subState = 0;
                    }
                } else if (subState == -1) {
                    if (b == CR) {
                        subState = -2;
                    } else if (b == DASH) {
                        subState = -3;
                    }
                } else if (subState == -2) {
                    if (b == LF) {
                        //ok, we have our data
                        subState = 0;
                        state = 1;
                        headers = new HeaderMap();
                        return;
                    } else if (b == DASH) {
                        subState = -3;
                    } else {
                        subState = -1;
                    }
                } else if (subState == -3) {
                    if (b == DASH) {
                        state = -1; //we are done
                        return;
                    } else {
                        subState = -1;
                    }
                }
            }
            //handle the data we read so far
            ByteBuffer retBuffer = buffer.duplicate();
            retBuffer.position(pos);
            partHandler.data(retBuffer);
        }

        public boolean isComplete() {
            return state == -1;
        }
    }


    public static class MalformedMessageException extends Exception {

    }

}
