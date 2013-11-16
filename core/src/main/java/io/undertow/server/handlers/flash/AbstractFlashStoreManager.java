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

/**
 * Abstract implementation of the {@link FlashStoreManager}.
 *
 * @author <a href="mailto:andrei.zinca@gmail.com">Andrei Zinca</a>
 */
public abstract class AbstractFlashStoreManager<T, K, V> implements FlashStoreManager<T, K, V> {

    @Override
    public void setAttribute(HttpServerExchange exchange, K name, V value) {
        setAttribute((T) exchange.getAttachment(FlashStoreManager.ATTACHMENT_KEY_OUT), name, value);
    }

    @Override
    public V getAttribute(HttpServerExchange exchange, K name) {
        V value = getAttribute((T) exchange.getAttachment(FlashStoreManager.ATTACHMENT_KEY_OUT), name);
        if (value != null) return value;
        return getAttribute((T) exchange.getAttachment(FlashStoreManager.ATTACHMENT_KEY_IN), name);
    }

    /**
     * Set an attribute in the store
     */
    protected abstract void setAttribute(T store, K name, V value);

    /**
     * Get an attribute from the store
     */
    protected abstract V getAttribute(T store, K name);

}
