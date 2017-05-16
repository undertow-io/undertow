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

package io.undertow.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

import io.undertow.UndertowMessages;

/**
 * @author ckozak
 * @author Stuart Douglas
 */
public class NewInstanceObjectPool<T> implements ObjectPool {

    private final Supplier<T> supplier;
    private final Consumer<T> consumer;

    public NewInstanceObjectPool(Supplier<T> supplier, Consumer<T> consumer) {
        this.supplier = supplier;
        this.consumer = consumer;
    }


    @Override
    public PooledObject allocate() {
        final T obj = supplier.get();
        return new PooledObject() {

            private volatile boolean closed = false;

            @Override
            public T getObject() {
                if(closed) {
                    throw UndertowMessages.MESSAGES.objectIsClosed();
                }
                return obj;
            }

            @Override
            public void close() {
                closed = true;
                consumer.accept(obj);
            }
        };
    }
}
