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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * SPDY settings parser
 *
 * @author Stuart Douglas
 */
class SpdySettingsParser extends SpdyPushBackParser {

    private int length = -1;

    private int count = 0;

    private final List<SpdySetting> settings = new ArrayList<>();

    public SpdySettingsParser(int frameLength) {
        super(frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource) {
        if (length == -1) {
            if (resource.remaining() < 4) {
                return;
            }
            length = (resource.get() & 0xFF) << 24;
            length += (resource.get() & 0xFF) << 16;
            length += (resource.get() & 0xFF) << 8;
            length += (resource.get() & 0xFF);
        }
        while (count < length) {
            if (resource.remaining() < 8) {
                return;
            }
            int flags = resource.get() & 0xFF;
            int id = (resource.get() & 0xFF) << 16;
            id += (resource.get() & 0xFF) << 8;
            id += (resource.get() & 0xFF);
            int value = (resource.get() & 0xFF) << 24;
            value += (resource.get() & 0xFF) << 16;
            value += (resource.get() & 0xFF) << 8;
            value += (resource.get() & 0xFF);
            boolean found = false;
            //according to the spec we MUST ignore duplicates
            for (SpdySetting existing : settings) {
                if (existing.getId() == id) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                settings.add(new SpdySetting(flags, id, value));
            }
            count++;
        }
    }

    public List<SpdySetting> getSettings() {
        return settings;
    }
}
