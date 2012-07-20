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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Deque;
import java.util.concurrent.ConcurrentMap;

import org.xnio.IoUtils;
import org.xnio.streams.ChannelOutputStream;
import sun.nio.ch.ChannelInputStream;
import tmp.texugo.TexugoMessages;
import tmp.texugo.server.HttpServerConnection;
import tmp.texugo.server.HttpServerExchange;
import tmp.texugo.util.Attachable;
import tmp.texugo.util.HeaderMap;
import tmp.texugo.util.StatusCodes;

/**
 * An HTTP server request/response exchange.  An instance of this class is constructed as soon as the request headers are
 * fully parsed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class BlockingHttpServerExchange implements Attachable {

    private final HttpServerExchange exchange;
    boolean responseStarted;
    private final OutputStream outputStream;
    private final InputStream inputStream;

    private static final byte[] NEWLINE = "\r\n".getBytes();
    private static final byte[] HEADER_END = ": ".getBytes();


    public BlockingHttpServerExchange(final HttpServerExchange exchange) {
        if (exchange.isResponseStarted()) {
            throw TexugoMessages.MESSAGES.handlerMustRunBeforeResponseStarted(BlockingHttpServerExchange.class);
        }
        this.exchange = exchange;
        this.inputStream = new BufferedInputStream(new ChannelInputStream(exchange.getRequestChannel()));
        this.outputStream = new BufferedOutputStream(new ChannelOutputStream(exchange.getResponseChannel()));
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
        return responseStarted;
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

    public void startResponse() throws IOException {
        if (responseStarted) {
            TexugoMessages.MESSAGES.responseAlreadyStarted();
        }
        final HeaderMap responseHeaders = getResponseHeaders();
        responseHeaders.lock();
        responseStarted = true;
        final OutputStream stream = outputStream;
        try {
            stream.write(getProtocol().getBytes());
            stream.write(' ');
            stream.write(Integer.toString(getResponseCode()).getBytes());
            stream.write(' ');
            stream.write(StatusCodes.getReason(getResponseCode()).getBytes());
            stream.write(NEWLINE);
            for (final String header : responseHeaders) {
                stream.write(header.getBytes());
                stream.write(HEADER_END);
                final Deque<String> values = responseHeaders.get(header);
                for (String value : values) {
                    stream.write(value.getBytes());
                    stream.write(' ');
                }
                stream.write(NEWLINE);
            }
            stream.write(NEWLINE);
        } catch (IOException e) {
            IoUtils.safeClose(outputStream);
            IoUtils.safeClose(inputStream);
        }
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
