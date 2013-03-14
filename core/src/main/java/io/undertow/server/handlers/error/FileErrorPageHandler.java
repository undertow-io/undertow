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

package io.undertow.server.handlers.error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.undertow.UndertowLogger;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpHandlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.Headers;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.FileAccess;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

/**
 * Handler that serves up a file from disk to serve as an error page.
 * <p/>
 * This handler does not server up and response codes by default, you must configure
 * the response codes it responds to.
 *
 * @author Stuart Douglas
 */
public class FileErrorPageHandler implements HttpHandler {

    private static final Logger log = Logger.getLogger("io.undertow.server.error.file");
    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    /**
     * The response codes that this handler will handle. If this is empty then this handler will have no effect.
     */
    private volatile Set<Integer> responseCodes;

    private volatile File file;

    public FileErrorPageHandler(final File file, final Integer... responseCodes) {
        this.file = file;
        this.responseCodes = new HashSet<Integer>(Arrays.asList(responseCodes));
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.addDefaultResponseListener(new DefaultResponseListener() {
            @Override
            public boolean handleDefaultResponse(final HttpServerExchange exchange) {
                Set<Integer> codes = responseCodes;
                if (!exchange.isResponseStarted() && codes.contains(exchange.getResponseCode())) {
                    serveFile(exchange);
                    return true;
                }
                return false;
            }
        });

        next.handleRequest(exchange);
    }

    private void serveFile(final HttpServerExchange exchange) {
        exchange.dispatch(new Runnable() {
            @Override
            public void run() {
                final FileChannel fileChannel;
                try {
                    try {
                        fileChannel = exchange.getConnection().getWorker().getXnio().openFile(file, FileAccess.READ_ONLY);
                    } catch (FileNotFoundException e) {
                        //TODO: how to handle this
                        exchange.endExchange();
                        return;
                    }
                } catch (IOException e) {
                    UndertowLogger.REQUEST_LOGGER.exceptionReadingFile(file.toPath(), e);
                    exchange.endExchange();
                    return;
                }

                final StreamSinkChannel response = exchange.getResponseChannel();
                response.getCloseSetter().set(new ChannelListener<Channel>() {
                    public void handleEvent(final Channel channel) {
                        IoUtils.safeClose(fileChannel);
                    }
                });
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, file.length());

                try {
                    log.tracef("Serving file %s (blocking)", fileChannel);
                    Channels.transferBlocking(response, fileChannel, 0, file.length());
                    log.tracef("Finished serving %s, shutting down (blocking)", fileChannel);
                    response.shutdownWrites();
                    log.tracef("Finished serving %s, flushing (blocking)", fileChannel);
                    Channels.flushBlocking(response);
                    log.tracef("Finished serving %s (complete)", fileChannel);
                    exchange.endExchange();
                } catch (IOException ignored) {
                    log.tracef("Failed to serve %s: %s", fileChannel, ignored);
                    exchange.endExchange();
                    IoUtils.safeClose(response);
                } finally {
                    IoUtils.safeClose(fileChannel);
                }
            }
        });
    }

    public HttpHandler getNext() {
        return next;
    }

    public FileErrorPageHandler setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    public Set<Integer> getResponseCodes() {
        return Collections.unmodifiableSet(responseCodes);
    }

    public FileErrorPageHandler setResponseCodes(final Set<Integer> responseCodes) {
        if (responseCodes == null) {
            this.responseCodes = Collections.emptySet();
        } else {
            this.responseCodes = new HashSet<Integer>(responseCodes);
        }
        return this;
    }

    public FileErrorPageHandler setResponseCodes(final Integer... responseCodes) {
        this.responseCodes = new HashSet<Integer>(Arrays.asList(responseCodes));
        return this;
    }

    public File getFile() {
        return file;
    }

    public FileErrorPageHandler setFile(final File file) {
        this.file = file;
        return this;
    }
}
