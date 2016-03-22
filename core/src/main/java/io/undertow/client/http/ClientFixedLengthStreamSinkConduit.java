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

package io.undertow.client.http;

import io.undertow.conduits.AbstractFixedLengthStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

class ClientFixedLengthStreamSinkConduit extends AbstractFixedLengthStreamSinkConduit {

    private final HttpClientExchange exchange;

    /**
     * Construct a new instance.
     *
     * @param next           the next channel
     * @param contentLength  the content length
     * @param configurable   {@code true} if this instance should pass configuration to the next
     * @param propagateClose {@code true} if this instance should pass close to the next
     * @param exchange
     */
    ClientFixedLengthStreamSinkConduit(StreamSinkConduit next, long contentLength, boolean configurable, boolean propagateClose, HttpClientExchange exchange) {
        super(next, contentLength, configurable, propagateClose);
        this.exchange = exchange;
    }



    @Override
    protected void channelFinished() {
        exchange.terminateRequest();
    }
}
