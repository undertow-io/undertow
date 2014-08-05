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

import java.io.IOException;
import java.nio.ByteBuffer;

import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
abstract class AbstractAjpParser {

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

        stringLength = -1;
        currentString = null;
        currentIntegerPart = -1;
        readHeaders = 0;
    }

    public abstract void parse(final ByteBuffer buf) throws IOException;

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

    public abstract boolean isComplete();

    protected abstract HttpString headers(int offset);

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
