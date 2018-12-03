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

package io.undertow.servlet.spec;

import java.io.IOException;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import io.undertow.server.HttpServerExchange;

/**
 * Servlet input stream implementation. This stream is non-buffered, and is used for both
 * HTTP requests and for upgraded streams.
 *
 * @author Stuart Douglas
 */
public class ServletInputStreamImpl extends ServletInputStream {
    private final HttpServerExchange exchange;

    public ServletInputStreamImpl(HttpServerExchange exchange) {
        this.exchange = exchange;
    }


    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        throw new RuntimeException("NYI");
    }

    @Override
    public int read() throws IOException {
        return exchange.getInputStream().read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return exchange.getInputStream().read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return exchange.getInputStream().read(b, off, len);
    }
}
