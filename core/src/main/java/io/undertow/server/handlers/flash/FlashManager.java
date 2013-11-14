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
 * The FlashManager works in conjunction with the {@link FlashHandler} and provides ways to set
 * and get data stored in the flash.
 *
 * @author <a href="mailto:andrei.zinca@gmail.com">Andrei Zinca</a>
 */
public interface FlashManager {

    public static final AttachmentKey FLASH_ATTACHMENT_KEY = AttachmentKey.create(Object.class);

    /**
     * Set attribute value
     */
    void setFlashAttribute(HttpServerExchange exchange, String key, Object value);

    /**
     * Get attribute value
     */
    Object getFlashAttribute(HttpServerExchange exchange, String key);
}
