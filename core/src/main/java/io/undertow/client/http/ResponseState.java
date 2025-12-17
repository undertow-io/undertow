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

package io.undertow.client.http;

import io.undertow.util.HttpString;

/**
 * @author <a href="mailto:opalka.richard@gmail.com">Richard Opalka</a>
 */
final class ResponseState {
    static final byte VERSION = 0;
    static final byte STATUS_CODE = 1;
    static final byte REASON_PHRASE = 2;
    static final byte FIELD_NAME = 3;
    static final byte FIELD_VALUE = 4;
    static final byte MESSAGE_BODY = 5;

    byte state;
    final StringBuilder parsedData = new StringBuilder();
    byte previousByte;
    HttpString headerName;

    ResponseState() {
    }

    boolean isComplete() {
        return state == MESSAGE_BODY;
    }
}

