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
import io.undertow.util.AttachmentKey;

/**
 * Defines two attachment keys, one for incoming flash and one for outgoing flash.
 * <p>Provides a method to build a flash store that the {@link FlashHandler} uses to initialize the incoming and outgoing
 * stores.
 * <p>Provides ways to get and set flash attributes.
 *
 * @author <a href="mailto:andrei.zinca@gmail.com">Andrei Zinca</a>
 */
public interface FlashStoreManager<T, K, V> {

    AttachmentKey ATTACHMENT_KEY_OUT = AttachmentKey.create(Object.class);
    AttachmentKey ATTACHMENT_KEY_IN = AttachmentKey.create(Object.class);

    T buildStore();

    void setAttribute(HttpServerExchange exchange, K name, V value);

    V getAttribute(HttpServerExchange exchange, K name);

}
