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

package tmp.texugo.server.handlers.blocking;

import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;
import tmp.texugo.server.HttpServerConnection;
import tmp.texugo.server.HttpServerExchange;
import tmp.texugo.util.Attachable;
import tmp.texugo.util.HeaderMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentMap;

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
public final class BlockingHttpServerExchange implements Attachable {

    private final HttpServerExchange exchange;
    private OutputStream out;
    private InputStream in;

    public BlockingHttpServerExchange(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    public String getProtocol() {
        return exchange.getProtocol();
    }

    public boolean isHttp09() {
        return exchange.isHttp09();
    }

    public boolean isHttp10() {
        return exchange.isHttp10();
    }

    public boolean isHttp11() {
        return exchange.isHttp11();
    }

    public String getRequestMethod() {
        return exchange.getRequestMethod();
    }

    public String getRequestScheme() {
        return exchange.getRequestScheme();
    }

    public String getRequestPath() {
        return exchange.getRequestPath();
    }

    public String getRelativePath() {
        return exchange.getRelativePath();
    }

    public String getCanonicalPath() {
        return exchange.getCanonicalPath();
    }

    public HttpServerConnection getConnection() {
        return exchange.getConnection();
    }

    /**
     * Get the source address of the HTTP request.
     *
     * @return the source address of the HTTP request
     */
    public InetSocketAddress getSourceAddress() {
        return exchange.getSourceAddress();
    }

    /**
     * Get the destination address of the HTTP request.
     *
     * @return the destination address of the HTTP request
     */
    public InetSocketAddress getDestinationAddress() {
        return exchange.getDestinationAddress();
    }

    /**
     * Get the request headers.
     *
     * @return the request headers
     */
    public HeaderMap getRequestHeaders() {
        return exchange.getRequestHeaders();
    }

    /**
     * Get the response headers.
     *
     * @return the response headers
     */
    public HeaderMap getResponseHeaders() {
        return exchange.getResponseHeaders();
    }

    /**
     * @return <code>true</code> If the response has already been started
     */
    public boolean isResponseStarted() {
        return exchange.isResponseStarted();
    }

    /**
     * Change the response code for this response.  If not specified, the code will be a {@code 200}.  Setting
     * the response code after the response headers have been transmitted has no effect.
     *
     * @param responseCode the new code
     * @throws IllegalStateException if a response or upgrade was already sent
     */
    public void setResponseCode(final int responseCode) {
        exchange.setResponseCode(responseCode);
    }

    /**
     * Get the response code.
     *
     * @return the response code
     */
    public int getResponseCode() {
        return exchange.getResponseCode();
    }

    public OutputStream getOutputStream() {
        if(out == null) {
            out = new BufferedOutputStream(new ChannelOutputStream(exchange.getResponseChannel()));
        }
        return out;
    }

    public InputStream getInputStream() {
        if(in == null) {
            in = new BufferedInputStream(new ChannelInputStream(exchange.getRequestChannel()));
        }
        return in;
    }

    @Override
    public Object getAttachment(final String name) {
        return exchange.getAttachment(name);
    }

    @Override
    public Object putAttachment(final String name, final Object value) {
        return exchange.putAttachment(name, value);
    }

    @Override
    public Object putAttachmentIfAbsent(final String name, final Object value) {
        return exchange.putAttachmentIfAbsent(name, value);
    }

    @Override
    public Object replaceAttachment(final String name, final Object newValue) {
        return exchange.replaceAttachment(name, newValue);
    }

    @Override
    public Object removeAttachment(final String name) {
        return exchange.removeAttachment(name);
    }

    @Override
    public boolean replaceAttachment(final String name, final Object expectValue, final Object newValue) {
        return exchange.replaceAttachment(name, expectValue, newValue);
    }

    @Override
    public boolean removeAttachment(final String name, final Object expectValue) {
        return exchange.removeAttachment(name, expectValue);
    }

    @Override
    public ConcurrentMap<String, Object> getAttachments() {
        return exchange.getAttachments();
    }
}
