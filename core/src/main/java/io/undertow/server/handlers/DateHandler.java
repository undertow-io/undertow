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

import java.util.Date;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;

/**
 * Class that adds the Date: header to a HTTP response.
 *
 * The current date string is cached, and is updated every second in a racey
 * manner (i.e. it is possible for two thread to update it at once).
 * <p>
 * This handler is deprecated, the same functionality is achieved by using the
 * server option {@link io.undertow.UndertowOptions#ALWAYS_SET_DATE ALWAYS_SET_DATE}.
 * It is enabled by default.
 *
 * @author Stuart Douglas
 */
@Deprecated()
public class DateHandler implements HttpHandler {

    private final HttpHandler next;
    private volatile String cachedDateString;
    private volatile long nextUpdateTime = -1;


    public DateHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        // better method is used in DateUtils#addDateHeaderIfRequired
        long time = System.nanoTime();
        if(time < nextUpdateTime) {
            exchange.getResponseHeaders().put(Headers.DATE, cachedDateString);
        } else {
            long realTime = System.currentTimeMillis();
            String dateString = DateUtils.toDateString(new Date(realTime));
            cachedDateString = dateString;
            nextUpdateTime = time + 1000000000;
            exchange.getResponseHeaders().put(Headers.DATE, dateString);
        }
        next.handleRequest(exchange);
    }


}
