/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.connector;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public interface IoResult<T> {

    T get() throws IOException;

    void addNotifier(BiConsumer<T, IOException> notifier);

    T get(long timeout, TimeUnit milliseconds);

    boolean isComplete();
    IoResult<Void> EMPTY = new IoResult<Void>() {
        @Override
        public Void get() throws IOException {
            return null;
        }

        @Override
        public void addNotifier(BiConsumer<Void, IOException> notifier) {
            notifier.accept(null, null);
        }

        @Override
        public Void get(long timeout, TimeUnit milliseconds) {
            return null;
        }

        @Override
        public boolean isComplete() {
            return true;
        }
    };

}
