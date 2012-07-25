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

package tmp.texugo.client;

import java.io.Closeable;
import java.net.URI;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.channels.ConnectedStreamChannel;
import tmp.texugo.util.Attachable;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class HttpClientConnection extends Attachable implements Closeable {
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
     */
    public abstract HttpClientRequest sendRequest(final String method, final URI target);

    public abstract IoFuture<ConnectedStreamChannel> upgradeToWebSocket(final String service, final OptionMap optionMap);
}
