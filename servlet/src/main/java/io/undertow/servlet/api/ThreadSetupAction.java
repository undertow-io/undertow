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
 * Interface that can be implemented by classes that need to setup
 * and thread local context before a request is processed.
 *
 * @author Stuart Douglas
 */
@Deprecated
public interface ThreadSetupAction {

    /**
     * Setup any thread local context
     *
     * @param exchange The exchange, this may be null
     * @return A handle to tear down the request when the invocation is finished, or null
     */
    Handle setup(final HttpServerExchange exchange);

    public interface Handle {
        void tearDown();
    }

}
