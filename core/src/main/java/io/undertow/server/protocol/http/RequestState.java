/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

package io.undertow.server.protocol.http;

import io.undertow.util.HttpString;

/**
 * @author <a href="mailto:opalka.richard@gmail.com">Richard Opalka</a>
 */
final class RequestState {

    // Parsing states
    static final byte METHOD = 0;
    static final byte REQUEST_TARGET = 1;
    static final byte VERSION = 2;
    static final byte FIELD_NAME = 3;
    static final byte FIELD_VALUE = 4;
    static final byte MESSAGE_BODY = 5;
    byte state;

    // Parsing substates
    static final byte SCHEME = 0;
    static final byte HOST = 1;
    static final byte PORT = 2;
    static final byte PATH_SEGMENTS = 3;
    static final byte PATH_PARAMS = 4;
    static final byte QUERY_PARAMS = 5;
    byte substate;

    // Target types
    static final byte UNKNOWN = 0;
    static final byte ORIGIN_FORM = 1;
    static final byte ABSOLUTE_FORM = 2;
    static final byte AUTHORITY_FORM = 3;
    static final byte ASTERISK_FORM = 4;
    byte targetType;

    // Address types
    static final byte UNSPECIFIED = 0;
    static final byte DNS = 1;
    static final byte IPV4 = 2;
    static final byte IPV6 = 3;
    byte addressType;

    int position;
    int count;
    boolean urlDecodeRequired;
    boolean paramDecodeRequired;
    final StringBuilder parsedData = new StringBuilder();
    final StringBuilder decodedData = new StringBuilder();
    final StringBuilder canonicalPath = new StringBuilder();
    byte previousByte;
    boolean doubleDotSegment;
    HttpString headerName;
    String paramName;

    boolean isComplete() {
        return state == MESSAGE_BODY;
    }

    void reset() {
        state = 0;
        substate = 0;
        targetType = 0;
        addressType = 0;
        position = 0;
        count = 0;
        urlDecodeRequired = false;
        paramDecodeRequired = false;
        decodedData.setLength(0);
        parsedData.setLength(0);
        canonicalPath.setLength(0);
        previousByte = 0;
        doubleDotSegment = false;
        headerName = null;
        paramName = null;
    }

    void setNext(final byte newState) {
        setNext(newState, SCHEME);
        urlDecodeRequired = false;
        paramDecodeRequired = false;
    }

    void setNext(final byte newState, final byte newSubstate) {
        state = newState;
        substate = newSubstate;
        position = 0;
        if (newState == VERSION || newSubstate == QUERY_PARAMS) count = 0;
        previousByte = 0;
        decodedData.setLength(0);
        parsedData.setLength(0);
        canonicalPath.setLength(0);
        doubleDotSegment = false;
        headerName = null;
        paramName = null;
    }
}
