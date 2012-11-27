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
package io.undertow.websockets.masking;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class Masker {

    private final byte[] maskingKey;
    int m = 0;

    public Masker(int maskingKey) {
        this.maskingKey = createsMaskingKey(maskingKey);
    }

    private static byte[] createsMaskingKey(int maskingKey) {
        byte[] key = new byte[4];
        key[0] = (byte) ((maskingKey >> 24) & 0xFF);
        key[1] = (byte) ((maskingKey >> 16) & 0xFF);
        key[2] = (byte) ((maskingKey >> 8) & 0xFF);
        key[3] = (byte) (maskingKey & 0xFF);
        return key;
    }

    private void mask(ByteBuffer buf, boolean flip) {
        ByteBuffer d;
        if (flip) {
            d = buf.duplicate();
            d.flip();
        } else {
            d = buf;
        }
        for (int i = d.position(); i < d.limit(); ++i) {
            d.put(i, (byte) (d.get(i) ^ maskingKey[m++]));
            m = m % 4;
        }
    }

    public void maskAfterRead(ByteBuffer buf) {
        mask(buf, true);
    }

    public void maskBeforeWrite(ByteBuffer buf) {
        mask(buf, false);
    }
}
