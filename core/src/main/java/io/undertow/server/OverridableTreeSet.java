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

import java.util.TreeSet;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class OverridableTreeSet<T> extends TreeSet<T> {
    @Override
    public boolean add(final T o) {
        // always override previous value
        super.remove(o);
        super.add(o);
        return true;
    }
}
