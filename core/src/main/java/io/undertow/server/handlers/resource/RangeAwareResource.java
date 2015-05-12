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

package io.undertow.server.handlers.resource;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;

/**
 * A resource implementation that
 *
 * @author Stuart Douglas
 */
public interface RangeAwareResource extends Resource {


    /**
     * Serve the resource, and call the provided callback when complete.
     *
     * @param sender The sender to use.
     * @param exchange The exchange
     */
    void serveRange(final Sender sender, final HttpServerExchange exchange, long start, long end, final IoCallback completionCallback);

    /**
     * It is possible that some resources managers may only support range requests on a subset of their resources,
     *
     * @return <code>true</code> if this resource supports range requests
     */
    boolean isRangeSupported();
}
