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

package io.undertow.protocols.http2;

/**
 * A Http2 Setting
 *
 * @author Stuart Douglas
 */
public class Http2Setting {

    public static final int SETTINGS_HEADER_TABLE_SIZE = 0x1;
    public static final int SETTINGS_ENABLE_PUSH = 0x2;
    public static final int SETTINGS_MAX_CONCURRENT_STREAMS = 0x3;
    public static final int SETTINGS_INITIAL_WINDOW_SIZE = 0x4;
    public static final int SETTINGS_MAX_FRAME_SIZE = 0x5;
    public static final int SETTINGS_MAX_HEADER_LIST_SIZE = 0x6;

    private final int id;
    private final long value;

    Http2Setting(int id, long value) {
        this.id = id;
        this.value = value;
    }

    public int getId() {
        return id;
    }

    public long getValue() {
        return value;
    }
}
