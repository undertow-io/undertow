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

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;

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
    private volatile HttpHandler noEncodingHandler = ResponseCodeHandler.HANDLE_406;

    private final ContentEncodingRepository contentEncodingRepository;

    public EncodingHandler(final HttpHandler next, ContentEncodingRepository contentEncodingRepository) {
        this.next = next;
        this.contentEncodingRepository = contentEncodingRepository;
    }

    public EncodingHandler(ContentEncodingRepository contentEncodingRepository) {
        this.contentEncodingRepository = contentEncodingRepository;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        AllowedContentEncodings encodings = contentEncodingRepository.getContentEncodings(exchange);
        if (encodings == null) {
            next.handleRequest(exchange);
        } else if (encodings.isNoEncodingsAllowed()) {
            noEncodingHandler.handleRequest(exchange);
        } else {
            exchange.addResponseWrapper(encodings);
            exchange.putAttachment(AllowedContentEncodings.ATTACHMENT_KEY, encodings);
            next.handleRequest(exchange);
        }
    }


    public HttpHandler getNext() {
        return next;
    }

    public EncodingHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }


    public HttpHandler getNoEncodingHandler() {
        return noEncodingHandler;
    }

    public EncodingHandler setNoEncodingHandler(HttpHandler noEncodingHandler) {
        Handlers.handlerNotNull(noEncodingHandler);
        this.noEncodingHandler = noEncodingHandler;
        return this;
    }


}
