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

package io.undertow.server.handlers.blocking;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import io.undertow.server.HttpServerExchange;
import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;

/**
 * An HTTP server request/response exchange.  An instance of this class is constructed as soon as the request headers are
 * fully parsed.
 * <p/>
 * This class is just a wrapper around {@link HttpServerExchange}.
 *
 * This class is not thread safe, it must be externally synchronized if it is used by multiple threads.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class BlockingHttpServerExchange {

    private final HttpServerExchange exchange;
    private OutputStream out;
    private InputStream in;

    public BlockingHttpServerExchange(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    /**
     *
     * @return The underlying http server exchange.
     */
    public HttpServerExchange getExchange() {
        return exchange;
    }

    public OutputStream getOutputStream() {
        if(out == null) {
            out = new BufferedOutputStream(new ChannelOutputStream(exchange.getResponseChannelFactory().create()));
        }
        return out;
    }

    public InputStream getInputStream() {
        if(in == null) {
            in = new BufferedInputStream(new ChannelInputStream(exchange.getRequestChannel()));
        }
        return in;
    }

}
