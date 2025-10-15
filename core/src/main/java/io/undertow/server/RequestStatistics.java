/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package io.undertow.server;

import io.undertow.UndertowOptions;

public final class RequestStatistics {
    private final HttpServerExchange exchange;

    public RequestStatistics(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    public long getBytesSent() {
        return exchange.getResponseBytesSent();
    }

    public long getBytesReceived() {
        return exchange.getRequestContentLength();
    }

    public long getStartTime() {
        return exchange.getRequestStartTime();
    }

    public long getProcessingTime() {
        if (exchange.getConnection().getUndertowOptions().get(UndertowOptions.RECORD_REQUEST_START_TIME, false)) {
            return System.nanoTime() - exchange.getRequestStartTime();
        } else {
            return -1;
        }
    }

    public String getQueryString() {
        return exchange.getQueryString();
    }

    public String getUri() {
        return exchange.getRequestURI();
    }

    public String getMethod() {
        return exchange.getRequestMethod().toString();
    }

    public String getProtocol() {
        return exchange.getProtocol().toString();
    }

    public String getRemoteAddress() {
        return exchange.getSourceAddress().toString();
    }
}
