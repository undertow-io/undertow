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
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;

import io.undertow.util.HttpString;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;
import io.undertow.util.AbstractAttachable;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class HttpClientConnection extends AbstractAttachable implements Closeable {
    private final HttpClient client;

    protected HttpClientConnection(final HttpClient client) {
        this.client = client;
    }

    public HttpClient getClient() {
        return client;
    }

    /**
     * Initiate an HTTP request on this connection.
     *
     * @param method the HTTP request method to use
     * @param target the target URI to access
     * @return the new request, or {@code null} if no more requests can be made on this connection
     * @throws IOException
     */
    public HttpClientRequest sendRequest(final String method, final URI target) throws IOException {
        return sendRequest(new HttpString(method), target);
    }

    /**
     * Initiate an HTTP request on this connection.
     *
     * @param method the HTTP request method to use
     * @param target the target URI to access
     * @return the new request, or{@code null} if no more request can be made on this connection
     * @throws IOException
     */
    public abstract HttpClientRequest sendRequest(final HttpString method, final URI target) throws IOException;

    /**
     * Upgrade this HTTP connection to a raw socket.
     *
     * @param service the service to upgrade to
     * @param optionMap the channel options
     * @return the future channel
     * @throws IOException
     */
    public abstract IoFuture<ConnectedStreamChannel> upgradeToWebSocket(final String service, final OptionMap optionMap) throws IOException;

    abstract OptionMap getOptions();
    abstract Pool<ByteBuffer> getBufferPool();

}
