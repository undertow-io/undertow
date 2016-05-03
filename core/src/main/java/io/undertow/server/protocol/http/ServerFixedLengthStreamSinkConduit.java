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

package io.undertow.server.protocol.http;

import io.undertow.conduits.AbstractFixedLengthStreamSinkConduit;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import org.xnio.conduits.StreamSinkConduit;

/**
 * @author Stuart Douglas
 */
public class ServerFixedLengthStreamSinkConduit extends AbstractFixedLengthStreamSinkConduit {

    private HttpServerExchange exchange;

    /**
     * Construct a new instance.
     *
     * @param next           the next channel
     * @param configurable   {@code true} if this instance should pass configuration to the next
     * @param propagateClose {@code true} if this instance should pass close to the next
     */
    public ServerFixedLengthStreamSinkConduit(StreamSinkConduit next, boolean configurable, boolean propagateClose) {
        super(next, 1, configurable, propagateClose);
    }

    void reset(long contentLength, HttpServerExchange exchange) {
        this.exchange = exchange;
        super.reset(contentLength, !exchange.isPersistent());
    }

    void clearExchange(){
        this.exchange = null;
    }

    @Override
    protected void channelFinished() {
        if(exchange != null) {
            Connectors.terminateResponse(exchange);
        }
    }
}
