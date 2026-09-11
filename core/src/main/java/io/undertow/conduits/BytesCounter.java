/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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
package io.undertow.conduits;

import java.io.IOException;

interface BytesCounter<T extends IOException> {
    BytesCounter<IOException> NULL = new BytesCounter<>() {
        @Override
        public void increment() {
            // does nothing
        }

        @Override
        public void add(long bytes) {
            // does nothing
        }
    };
    void increment() throws T;
    void add(long bytes) throws T;
}
