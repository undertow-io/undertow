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

package io.undertow.io;

import java.io.IOException;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.IoUtils;

/**
 * @author Stuart Douglas
 */
public interface IoCallback<T> {

    void onComplete(final HttpServerExchange exchange, final T context);

    default void onException(final HttpServerExchange exchange, final T context, final IOException exception) {
        UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
        exchange.endExchange();
    }

    /**
     * A default callback that simply ends the exchange.
     */
    IoCallback END_EXCHANGE = new IoCallback<Object>() {
        @Override
        public void onComplete(HttpServerExchange exchange, Object context) {
            exchange.endExchange();
        }
    };

}
