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

import org.xnio.Pooled;

/**
 * Wrapper that allows you to use a non-pooed item as a pooled value
 *
 * @author Stuart Douglas
 */
public class ImmediatePooled<T> implements Pooled<T> {

    private final T value;

    public ImmediatePooled(T value) {
        this.value = value;
    }

    @Override
    public void discard() {
    }

    @Override
    public void free() {
    }

    @Override
    public T getResource() throws IllegalStateException {
        return value;
    }

    @Override
    public void close() {
    }
}
