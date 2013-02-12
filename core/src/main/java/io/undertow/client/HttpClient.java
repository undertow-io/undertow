/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package io.undertow.client;

import java.io.Closeable;
import java.net.SocketAddress;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class HttpClient implements Closeable {
    private final XnioWorker worker;

    protected HttpClient(final XnioWorker worker) {
        this.worker = worker;
    }

    public XnioWorker getWorker() {
        return worker;
    }

    /**
     * Connect to a remote HTTP server.
     *
     * @param destination the destination
     * @param optionMap the connection options
     * @return an HTTP client connection
     */
    public abstract IoFuture<HttpClientConnection> connect(final SocketAddress destination, final OptionMap optionMap);

    /**
     * Send a request, managing connections automatically.
     *
     * @param method the HTTP method to use (see {@link io.undertow.util.Methods})
     * @param requestUri the URI to connect to
     * @param optionMap the request options
     * @return the future request
     */
    public abstract IoFuture<HttpClientRequest> sendRequest(final String method, final String requestUri, final OptionMap optionMap);
}
