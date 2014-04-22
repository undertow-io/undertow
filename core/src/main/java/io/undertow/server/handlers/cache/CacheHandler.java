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

package io.undertow.server.handlers.cache;

import io.undertow.Handlers;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.encoding.AllowedContentEncodings;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSinkConduit;

import static io.undertow.util.Headers.CONTENT_LENGTH;

/**
 *
 * Handler that attaches a cache to the exchange, a handler can query this cache to see if the
 * cache has a cached copy of the content, and if so have the cache serve this content automatically.
 *
 *
 * @author Stuart Douglas
 */
public class CacheHandler implements HttpHandler {

    private final DirectBufferCache cache;
    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    public CacheHandler(final DirectBufferCache cache, final HttpHandler next) {
        this.cache = cache;
        this.next = next;
    }

    public CacheHandler(final DirectBufferCache cache) {
        this.cache = cache;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final ResponseCache responseCache = new ResponseCache(cache, exchange);
        exchange.putAttachment(ResponseCache.ATTACHMENT_KEY, responseCache);
        exchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
                if(!responseCache.isResponseCachable()) {
                    return factory.create();
                }
                final AllowedContentEncodings contentEncodings = exchange.getAttachment(AllowedContentEncodings.ATTACHMENT_KEY);
                if(contentEncodings != null) {
                    if(!contentEncodings.isIdentity()) {
                        //we can't cache content encoded responses, as we have no idea how big they will end up being
                        return factory.create();
                    }
                }
                String lengthString = exchange.getResponseHeaders().getFirst(CONTENT_LENGTH);
                if(lengthString == null) {
                    //we don't cache chunked requests
                    return factory.create();
                }
                int length = Integer.parseInt(lengthString);
                final CachedHttpRequest key = new CachedHttpRequest(exchange);
                final DirectBufferCache.CacheEntry entry = cache.add(key, length);

                if (entry == null || entry.buffers().length == 0 || !entry.claimEnable()) {
                    return factory.create();
                }

                if (!entry.reference()) {
                    entry.disable();
                    return factory.create();
                }

                return new ResponseCachingStreamSinkConduit(factory.create(), entry, length);
            }
        });
        next.handleRequest(exchange);
    }

    public HttpHandler getNext() {
        return next;
    }

    public CacheHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }
}
