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

/**
 * Listener interface for events that are run at the completion of a request/response
 * cycle (i.e. when the request has been completely read, and the response has been fully written).
 *
 * At this point it is too late to modify the exchange further.
 *
 * Implementations are required invoke {@link NextListener#proceed()} to allow other listeners to release
 * resources, even in failure scenarios. This chain allows transfer of request ownership between
 * listeners in order to complete using non-blocking mechanisms, and must not be used to conditionally
 * proceed.
 *
 * Completion listeners are invoked in reverse order.
 *
 * @author Stuart Douglas
 */
public interface ExchangeCompletionListener {

    void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener);

    interface NextListener {

        /**
         * Invokes the next {@link ExchangeCompletionListener listener}. This must be executed by
         * every {@link ExchangeCompletionListener} implementation, and may be invoked from a
         * different thread as a callback. Failure to proceed will cause resource leaks, and
         * potentially cause requests to hang.
         */
        void proceed();

    }
}
