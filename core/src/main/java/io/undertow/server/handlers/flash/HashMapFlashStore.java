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
import java.util.Map;

/**
 * A {@link FlashStore} implementation that uses a {@link HashMap} for the store.
 *
 * @author <a href="mailto:andrei.zinca@gmail.com">Andrei Zinca</a>
 */
public class HashMapFlashStore implements FlashStore {

    public void setAttribute(HttpServerExchange exchange, String name, Object value) {
        getOrCreateFlashStore(exchange).put(name, value);
    }

    public Object getAttribute(HttpServerExchange exchange, String name) {
        Map flash = (Map) exchange.getAttachment(ATTACHMENT_KEY);
        if (flash == null) {
            return null;
        }
        return flash.get(name);
    }

    private Map getOrCreateFlashStore(HttpServerExchange exchange) {
        Map flash = (Map) exchange.getAttachment(ATTACHMENT_KEY);
        if (flash == null) {
            flash = new HashMap();
            exchange.putAttachment(ATTACHMENT_KEY, flash);
        }
        return flash;
    }
}