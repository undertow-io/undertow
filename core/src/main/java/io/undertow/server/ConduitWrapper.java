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

import io.undertow.util.ConduitFactory;
import org.xnio.conduits.Conduit;

/**
 * Interface that provides a means of wrapping a {@link Conduit}.  Every conduit wrapper has a chance
 * to replace the conduit with a conduit which either wraps or replaces the passed in conduit.  However it is the responsibility
 * of either the conduit wrapper instance or the conduit it creates to ensure that the original conduit is eventually
 * cleaned up and shut down properly when the request is terminated.
 *
 * @author Stuart Douglas
 */
public interface ConduitWrapper<T extends Conduit> {

    /**
     * Wrap the conduit.  The wrapper should not return {@code null}.  If no wrapping is desired, the original
     * conduit should be returned.
     *
     * @param factory the original conduit
     * @param exchange the in-flight HTTP exchange
     * @return the replacement conduit
     */
    T wrap(final ConduitFactory<T> factory, final HttpServerExchange exchange);
}
