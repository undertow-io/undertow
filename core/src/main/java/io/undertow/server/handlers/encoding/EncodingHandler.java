/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.encoding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;
import io.undertow.util.QValueParser;

/**
 * Handler that serves as the basis for content encoding implementations.
 * <p/>
 * Encoding handlers are added as delegates to this handler, with a specified server side priority.
 * <p/>
 * If a request comes in with no q value then then server will pick the handler with the highest priority
 * as the encoding to use, otherwise the q value will be used to determine the correct handler.
 * <p/>
 * If no handler matches then the identity encoding is assumed. If the identity encoding has been
 * specifically disallowed due to a q value of 0 then the handler will set the response code
 * 406 (Not Acceptable) and return.
 *
 * @author Stuart Douglas
 */
public class EncodingHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    private final Map<String, EncodingMapping> encodingMap = new CopyOnWriteMap<String, EncodingMapping>();

    private volatile HttpHandler noEncodingHandler = ResponseCodeHandler.HANDLE_406;

    private static final String IDENTITY = "identity";

    public EncodingHandler(final HttpHandler next) {
        this.next = next;
    }

    public EncodingHandler() {
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        final List<String> res = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING);
        HttpHandler nextHandler = this.next;
        if (res == null || res.isEmpty()) {
            if (nextHandler != null) {
                HttpHandlers.executeHandler(nextHandler, exchange);
            } else {
                //we don't have an identity handler
                HttpHandlers.executeHandler(noEncodingHandler, exchange);
            }
            return;
        }
        final List<EncodingMapping> resultingMappings = new ArrayList<>();
        final List<List<QValueParser.QValueResult>> found = QValueParser.parse(res);
        for (List<QValueParser.QValueResult> result : found) {
            List<EncodingMapping> available = new ArrayList<EncodingMapping>();
            boolean includesIdentity = false;
            boolean isQValue0 = false;

            for (final QValueParser.QValueResult value : result) {
                EncodingMapping encoding;
                if (value.getValue().equals("*")) {
                    includesIdentity = true;
                    encoding = new EncodingMapping(IDENTITY, ContentEncodingProvider.IDENTITY, 0, Predicates.<HttpServerExchange>truePredicate());
                } else {
                    encoding = encodingMap.get(value.getValue());
                }
                if (value.isQValueZero()) {
                    isQValue0 = true;
                }
                if (encoding != null) {
                    available.add(encoding);
                }
            }
            if (isQValue0) {
                if (resultingMappings.isEmpty()) {
                    if (includesIdentity) {
                        HttpHandlers.executeHandler(noEncodingHandler, exchange);
                        return;
                    } else {
                        HttpHandlers.executeHandler(nextHandler, exchange);
                        return;
                    }
                }
            } else if (!available.isEmpty()) {
                Collections.sort(available, Collections.reverseOrder());
                resultingMappings.addAll(available);
            }
        }
        if (!resultingMappings.isEmpty()) {
            final ContentEncoding contentEncoding = new ContentEncoding(exchange, resultingMappings);
            exchange.addResponseWrapper(contentEncoding);
            exchange.putAttachment(ContentEncoding.CONENT_ENCODING, contentEncoding);
        }
        HttpHandlers.executeHandler(nextHandler, exchange);
    }


    public HttpHandler getNext() {
        return next;
    }

    public EncodingHandler setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    public synchronized EncodingHandler addEncodingHandler(final String encoding, final ContentEncodingProvider encoder, int priority) {
        addEncodingHandler(encoding, encoder, priority, Predicates.<HttpServerExchange>truePredicate());
        return this;
    }

    public synchronized EncodingHandler addEncodingHandler(final String encoding, final ContentEncodingProvider encoder, int priority, final Predicate<HttpServerExchange> enabledPredicate) {
        this.encodingMap.put(encoding, new EncodingMapping(encoding, encoder, priority, enabledPredicate));
        return this;
    }

    public synchronized EncodingHandler removeEncodingHandler(final String encoding) {
        encodingMap.remove(encoding);
        return this;
    }

    public HttpHandler getNoEncodingHandler() {
        return noEncodingHandler;
    }

    public EncodingHandler setNoEncodingHandler(HttpHandler noEncodingHandler) {
        HttpHandlers.handlerNotNull(noEncodingHandler);
        this.noEncodingHandler = noEncodingHandler;
        return this;
    }


}
