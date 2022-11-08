/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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
package io.undertow.server;

import java.util.Iterator;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class DelegatingIterable<E,V> implements Iterable<V> {

    private final MultiValueHashListStorage<E,V> delegate;

    DelegatingIterable(final MultiValueHashListStorage<E,V> delegate) {
        this.delegate = delegate;
    }

    MultiValueHashListStorage<E,V> getDelegate() {
        return delegate;
    }

    @Override
    public Iterator<V> iterator() {
        return delegate.valuesIterator();
    }

}
