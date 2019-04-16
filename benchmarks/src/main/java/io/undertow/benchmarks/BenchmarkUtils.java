/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package io.undertow.benchmarks;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility functionality used by benchmarks.
 *
 * @author Carter Kozak
 */
final class BenchmarkUtils {

    private static final byte[] GARBAGE_BUFFER = new byte[16 * 1024];

    /** Consumes the {@link InputStream}, returning the number of bytes read. */
    static long length(InputStream stream) throws IOException {
        long total = 0;
        while (true) {
            int read = stream.read(GARBAGE_BUFFER);
            if (read == -1) {
                return total;
            }
            total += read;
        }
    }

    private BenchmarkUtils() {}
}
