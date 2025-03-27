/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package io.undertow.server.handlers;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * A handler which simply sets a response code.
 *
 * @author <a href="mailto:bbaranow@redhat.com">Bartosz Baranowski</a>
 */
public final class ReasonPhraseHandler implements HttpHandler {

    private static final boolean debugEnabled;

    static {
        debugEnabled = UndertowLogger.PREDICATE_LOGGER.isDebugEnabled();
    }

    private final String reasonPhrase;

    private final HttpHandler next;
    /**
     * Construct a new instance.
     *
     * @param reasonPhrase the reason phrase to be set in status line
     */
    public ReasonPhraseHandler(final HttpHandler next, final String reasonPhrase) {
        this.next = next;
        this.reasonPhrase = reasonPhrase;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.setReasonPhrase(reasonPhrase);
        if(debugEnabled) {
            UndertowLogger.PREDICATE_LOGGER.debugf("Reason phrase set to [%s] for %s.", this.reasonPhrase, exchange);
        }
        if(next != null) {
            next.handleRequest(exchange);
        }
    }

    @Override
    public String toString() {
        return "reason-phrase( " + this.reasonPhrase + " )";
    }
}
