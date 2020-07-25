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

package io.undertow.server.handlers;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * A handler which simply sets a response code.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ResponseCodeHandler implements HttpHandler {

    private static final boolean debugEnabled;

    static {
        debugEnabled = UndertowLogger.PREDICATE_LOGGER.isDebugEnabled();
    }

    /**
     * A handler which sets a 200 code. This is the default response code, so in most cases
     * this simply has the result of finishing the request
     */
    public static final ResponseCodeHandler HANDLE_200 = new ResponseCodeHandler(200);

    /**
     * A handler which sets a 403 code.
     */
    public static final ResponseCodeHandler HANDLE_403 = new ResponseCodeHandler(403);
    /**
     * A handler which sets a 404 code.
     */
    public static final ResponseCodeHandler HANDLE_404 = new ResponseCodeHandler(404);
    /**
     * A handler which sets a 405 code.
     */
    public static final ResponseCodeHandler HANDLE_405 = new ResponseCodeHandler(405);
    /**
     * A handler which sets a 406 code.
     */
    public static final ResponseCodeHandler HANDLE_406 = new ResponseCodeHandler(406);
    /**
     * A handler which sets a 500 code.
     */
    public static final ResponseCodeHandler HANDLE_500 = new ResponseCodeHandler(500);

    private final int responseCode;

    /**
     * Construct a new instance.
     *
     * @param responseCode the response code to set
     */
    public ResponseCodeHandler(final int responseCode) {
        this.responseCode = responseCode;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.setStatusCode(responseCode);
        if(debugEnabled) {
            UndertowLogger.PREDICATE_LOGGER.debugf("Response code set to [%s] for %s.", responseCode, exchange);
        }
    }

    @Override
    public String toString() {
        return "response-code( " + this.responseCode + " )";
    }
}
