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

package tmp.texugo.server.handlers;

import tmp.texugo.server.HttpCompletionHandler;
import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;

/**
 * A handler which simply sets a response code.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ResponseCodeHandler implements HttpHandler {

    /**
     * A handler which sets a 404 code.
     */
    public static final ResponseCodeHandler HANDLE_404 = new ResponseCodeHandler(404);

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

    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        exchange.setResponseCode(responseCode);
        completionHandler.handleComplete();
    }
}
