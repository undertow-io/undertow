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

import io.undertow.util.HttpString;
import org.xnio.channels.StreamSourceChannel;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;

/**
 * A http response.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Emanuel Muckenhuber
 */
public final class HttpClientResponse extends AbstractAttachable {

    private final HttpClientRequest request;
    private final String reason;
    private final int responseCode;
    private final HeaderMap headers;
    private final long contentLength;
    private final HttpString protocol;
    private final StreamSourceChannel sourceChannel;


    protected HttpClientResponse(final PendingHttpRequest responseBuilder, final HttpClientRequest request, final long contentLength, final StreamSourceChannel sourceChannel) {
        this.request = request;
        this.protocol = responseBuilder.getProtocol();
        this.reason = responseBuilder.getReasonPhrase();
        this.responseCode = responseBuilder.getStatusCode();
        this.headers = responseBuilder.getResponseHeaders();

        this.contentLength = contentLength;
        this.sourceChannel = sourceChannel;
    }

    /**
     * Get the http response code.
     *
     * @return the response code
     */
    public int getResponseCode() {
        return responseCode;
    }

    /**
     * Get the content length. A content-length of <code>-1</code> declares
     * a unknown content-length.
     *
     * @return the content length
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * Get the response headers.
     *
     * @return the response headers
     */
    public HeaderMap getResponseHeaders() {
        return headers;
    }

    /**
     * Read the reply body.
     *
     * @return the response channel
     * @throws IOException
     */
    public StreamSourceChannel readReplyBody() throws IOException {
        return sourceChannel;
    }

    /**
     * Get the http reason phrase.
     *
     * @return the reason phrase
     */
    public String getReasonPhrase() {
        return reason;
    }

    /**
     *
     * @return The client request
     */
    public HttpClientRequest getRequest() {
        return request;
    }

    @Override
    public String toString() {
        return "HttpClientResponse{" +
                protocol + " " + responseCode + " " + reason +
                ", headers=" + headers +
                '}';
    }

}
