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

package io.undertow.server.handlers.form;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;

/**
 * Handler that eagerly parses form data. The request chain will pause while the data is being read,
 * and then continue when the form data is fully passed.
 * <p>
 * <p>
 * NOTE: This is not strictly compatible with servlet, as it removes the option for the user to
 * parse the request themselves, however in practice this requirement is probably rare, and
 * using this handler gives a significant performance advantage in that a thread is not blocked
 * for the duration of the upload.
 *
 * @author Stuart Douglas
 */
public class EagerFormParsingHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;
    private final FormParserFactory formParserFactory;

    public EagerFormParsingHandler(final FormParserFactory formParserFactory) {
        this.formParserFactory = formParserFactory;
    }

    public EagerFormParsingHandler() {
        this.formParserFactory = FormParserFactory.builder().build();
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        FormDataParser parser = formParserFactory.createParser(exchange);
        if (parser == null) {
            next.handleRequest(exchange);
            return;
        }
        if(exchange.isBlocking()) {
            exchange.putAttachment(FormDataParser.FORM_DATA, parser.parseBlocking());
            next.handleRequest(exchange);
        } else {
            parser.parse(next);
        }
    }

    public HttpHandler getNext() {
        return next;
    }

    public EagerFormParsingHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }
}
