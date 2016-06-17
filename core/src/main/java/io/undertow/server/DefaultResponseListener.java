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

package io.undertow.server;

import io.undertow.util.AttachmentKey;

/**
 * Listener interface for default response handlers. These are handlers that generate default content
 * such as error pages.
 *
 * @author Stuart Douglas
 */
public interface DefaultResponseListener {

    /**
     * If the default response listener was invoked as a result of an exception being thrown
     * then the exception will be available under this attachment key.
     */
    AttachmentKey<Throwable> EXCEPTION = AttachmentKey.create(Throwable.class);

    /**
     *
     * @param exchange The exchange
     * @return true if this listener is generating a default response.
     */
    boolean handleDefaultResponse(final HttpServerExchange exchange);
}
