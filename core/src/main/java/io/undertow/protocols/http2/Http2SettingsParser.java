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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
class Http2SettingsParser extends Http2PushBackParser {

    private int count = 0;

    private final List<Http2Setting> settings = new ArrayList<>();

    Http2SettingsParser(int frameLength) {
        super(frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource, Http2FrameHeaderParser parser) {
        while (count < parser.length) {
            if (resource.remaining() < 6) {
                return;
            }
            int id = (resource.get() & 0xFF) << 8;
            id += (resource.get() & 0xFF);
            long value = (resource.get() & 0xFFL) << 24;
            value += (resource.get() & 0xFFL) << 16;
            value += (resource.get() & 0xFFL) << 8;
            value += (resource.get() & 0xFFL);
            settings.add(new Http2Setting(id, value));
            count += 6;
        }
    }

    public List<Http2Setting> getSettings() {
        return settings;
    }
}
