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

package io.undertow.protocols.spdy;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public class StreamErrorException extends IOException {

    public static final int PROTOCOL_ERROR = 1;
    public static final int INVALID_STREAM = 2;
    public static final int REFUSED_STREAM = 3;
    public static final int UNSUPPORTED_VERSION = 4;
    public static final int CANCEL = 5;
    public static final int INTERNAL_ERROR = 6;
    public static final int FLOW_CONTROL_ERROR = 7;
    public static final int STREAM_IN_USE = 8;
    public static final int STREAM_ALREADY_CLOSED = 9;

    public static final int FRAME_TOO_LARGE = 11;

    private final int errorId;

    public StreamErrorException(int errorId) {
        this.errorId = errorId;
    }

    public int getErrorId() {
        return errorId;
    }
}
