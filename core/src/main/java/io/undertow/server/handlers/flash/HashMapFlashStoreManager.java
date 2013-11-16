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

import io.undertow.server.HttpServerExchange;

import java.util.HashMap;


/**
 * HashMap implementation of the {@link FlashStoreManager}.
 *
 * @author <a href="mailto:andrei.zinca@gmail.com">Andrei Zinca</a>
 */
public class HashMapFlashStoreManager<K, V> implements FlashStoreManager<K, V> {

    @Override
    public HashMap buildStore() {
        return new HashMap();
    }

    @Override
    public void setAttribute(HttpServerExchange exchange, K name, V value) {
        HashMap<K, V> current = (HashMap<K, V>) exchange.getAttachment(FlashStoreManager.ATTACHMENT_KEY_IN);
        current.put(name, value);
        HashMap<K, V> outgoing = (HashMap<K, V>) exchange.getAttachment(FlashStoreManager.ATTACHMENT_KEY_OUT);
        outgoing.put(name, value);
    }

    @Override
    public V getAttribute(HttpServerExchange exchange, K name) {
        return ((HashMap<K, V>) exchange.getAttachment(FlashStoreManager.ATTACHMENT_KEY_IN)).get(name);
    }
}
