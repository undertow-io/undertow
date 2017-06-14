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

package io.undertow.server.handlers.encoding;

import io.undertow.UndertowLogger;
import io.undertow.conduits.DeflaterPool;
import io.undertow.conduits.GzipStreamSinkConduit;
import io.undertow.conduits.NewInstanceDeflaterPool;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSinkConduit;

import java.util.zip.Deflater;

/**
 * Content coding for 'deflate'
 *
 * @author Stuart Douglas
 */
public class GzipEncodingProvider implements ContentEncodingProvider {

    private final DeflaterPool deflaterPool;

    public GzipEncodingProvider() {
        this(Deflater.DEFAULT_COMPRESSION);
    }

    public GzipEncodingProvider(int deflateLevel) {
        this(new NewInstanceDeflaterPool(deflateLevel, true));
    }

    public GzipEncodingProvider(DeflaterPool deflaterPool) {
        this.deflaterPool = deflaterPool;
    }

    @Override
    public ConduitWrapper<StreamSinkConduit> getResponseWrapper() {
        return new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
                UndertowLogger.REQUEST_LOGGER.tracef("Created GZIP response conduit for %s", exchange);
                return new GzipStreamSinkConduit(factory, exchange, deflaterPool);
            }
        };
    }
}
