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

package io.undertow.server.handlers.error;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringWriteChannelListener;
import org.xnio.channels.StreamSinkChannel;

/**
 * Handler that generates an extremely simple no frills error page
 *
 * @author Stuart Douglas
 */
public class SimpleErrorPageHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        HttpHandlers.executeHandler(next, exchange, new HttpCompletionHandler() {
            @Override
            public void handleComplete() {
                if (!exchange.isResponseStarted() &&
                        exchange.getResponseCode() >= 400) {
                    final StreamSinkChannel response = exchange.getResponseChannel();
                    final String errorPage = "<html><head><title>Error</title></head><body>" + exchange.getResponseCode() + " - " + StatusCodes.getReason(exchange.getResponseCode()) + "</body></html>";
                    StringWriteChannelListener listener = new StringWriteChannelListener(errorPage) {
                        @Override
                        protected void writeDone(final StreamSinkChannel channel) {
                            completionHandler.handleComplete();
                        }
                    };
                    listener.setup(response);
                } else {
                    completionHandler.handleComplete();
                }
            }
        });
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        this.next = next;
    }
}
