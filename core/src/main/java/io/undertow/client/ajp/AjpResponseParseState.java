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
