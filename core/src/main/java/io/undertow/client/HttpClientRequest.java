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

import java.io.IOException;
import java.net.URI;

import org.xnio.IoFuture;
import org.xnio.channels.StreamSinkChannel;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;

import static io.undertow.client.HttpClientUtils.addCallback;

/**
 * A http request.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Emanuel Muckenhuber
 */
public abstract class HttpClientRequest extends AbstractAttachable {

    private final HttpClientConnection connection;
    private final HeaderMap requestHeaders = new HeaderMap();

    protected HttpClientRequest(final HttpClientConnection connection) {
        this.connection = connection;
    }

    /**
     * Get the request method.
     *
     * @return the request method
     */
    public abstract String getMethod();

    /**
     * Get the request URI.
     *
     * @return the request uri
     */
    public abstract URI getTarget();

    /**
     * Get the http protocol.
     *
     * @return the http protocol
     */
    public abstract String getProtocol();

    /**
     * Get the associated http connection.
     *
     * @return the http connection
     */
    public HttpClientConnection getConnection() {
        return connection;
    }

    /**
     * Get the http request headers.
     *
     * @return the request headers
     */
    public final HeaderMap getRequestHeaders() {
        return requestHeaders;
    }

    /**
     * Write a http request without request body.
     *
     * @return the future http response
     * @throws IOException
     */
    public IoFuture<HttpClientResponse> writeRequest(){
        writeRequestBody(0);
        return getResponse();
    }

    /**
     * Write a http request with a given content-length. A content-length of <code>-1</code> can be used to declare a
     * unknown content-length and will result in a chunked transfer.
     *
     * @param contentLength the content length
     * @return the request channel
     * @throws IOException
     */
    public abstract StreamSinkChannel writeRequestBody(long contentLength);

    /**
     * Get the future response.
     *
     * @return the response future
     */
    public abstract IoFuture<HttpClientResponse> getResponse();

    /**
     * Write a http request without request body.
     *
     * @param responseCallback the response completion handler
     * @throws IOException
     */
    public void writeRequest(final HttpClientCallback<HttpClientResponse> responseCallback){
        final IoFuture<HttpClientResponse> response = writeRequest();
        addCallback(response, responseCallback);
    }

    /**
     * Write a http request with a given content-length. A content-length of <code>-1</code> can be used to declare a
     * unknown content-length and will result in a chunked transfer.
     *
     * @param contentLength the content length
     * @param responseCallback the response completion handler
     * @return the request channel
     * @throws IOException
     */
    public StreamSinkChannel writeRequestBody(long contentLength, final HttpClientCallback<HttpClientResponse> responseCallback) throws IOException {
        final StreamSinkChannel channel = writeRequestBody(contentLength);
        final IoFuture<HttpClientResponse> response = getResponse();
        addCallback(response, responseCallback);
        return channel;
    }

}
