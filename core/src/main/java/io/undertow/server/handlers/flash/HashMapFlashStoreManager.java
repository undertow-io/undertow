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

package io.undertow.server.handlers.flash;

import java.util.HashMap;


/**
 * {@link HashMap} implementation of the {@link FlashStoreManager}.
 *
 * @author <a href="mailto:andrei.zinca@gmail.com">Andrei Zinca</a>
 */
public class HashMapFlashStoreManager<K, V> extends AbstractFlashStoreManager<K, V> {

    @Override
    public HashMap buildStore() {
        return new HashMap();
    }

    @Override
    protected void setAttribute(Object store, K name, V value) {
        ((HashMap<K, V>) store).put(name, value);
    }

    @Override
    protected V getAttribute(Object store, K name) {
        return ((HashMap<K, V>) store).get(name);
    }

}
