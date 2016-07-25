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

package io.undertow.servlet.api;

import io.undertow.server.HttpServerExchange;

/**
 * Class that allows legacy thread setup actions to still be used
 *
 * @author Stuart Douglas
 */
@SuppressWarnings("deprecation")
class LegacyThreadSetupActionWrapper implements ThreadSetupHandler {

    private final ThreadSetupAction setupAction;

    LegacyThreadSetupActionWrapper(ThreadSetupAction setupAction) {
        this.setupAction = setupAction;
    }

    @Override
    public <T, C> Action<T, C> create(final Action<T, C> action) {
        return new Action<T, C>() {
            @Override
            public T call(HttpServerExchange exchange, C context) throws Exception {
                ThreadSetupAction.Handle handle = setupAction.setup(exchange);
                try {
                    return action.call(exchange, context);
                } finally {
                    if (handle != null) {
                        handle.tearDown();
                    }
                }
            }
        };
    }
}
