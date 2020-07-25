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

package io.undertow.server.handlers.error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.undertow.util.MimeMappings;
import org.jboss.logging.Logger;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

import io.undertow.Handlers;
import io.undertow.UndertowLogger;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.Headers;

/**
 * Handler that serves up a file from disk to serve as an error page.
 * <p>
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

    private volatile Path file;

    private final MimeMappings mimeMappings;

    @Deprecated
    public FileErrorPageHandler(final File file, final Integer... responseCodes) {
        this(file.toPath(), responseCodes);
    }

    public FileErrorPageHandler(final Path file, final Integer... responseCodes) {
        this.file = file;
        this.responseCodes = new HashSet<>(Arrays.asList(responseCodes));
        this.mimeMappings = MimeMappings.DEFAULT;
    }

    @Deprecated
    public FileErrorPageHandler(HttpHandler next, final File file, final Integer... responseCodes) {
        this(next, file.toPath(), responseCodes);
    }

    public FileErrorPageHandler(HttpHandler next, final Path file, final Integer... responseCodes) {
        this(next, file, MimeMappings.DEFAULT, responseCodes);
    }

    public FileErrorPageHandler(HttpHandler next, final Path file, MimeMappings mimeMappings, final Integer... responseCodes) {
        this.next = next;
        this.file = file;
        this.responseCodes = new HashSet<>(Arrays.asList(responseCodes));
        this.mimeMappings = mimeMappings;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.addDefaultResponseListener(new DefaultResponseListener() {
            @Override
            public boolean handleDefaultResponse(final HttpServerExchange exchange) {
                Set<Integer> codes = responseCodes;
                if (!exchange.isResponseStarted() && codes.contains(exchange.getStatusCode())) {
                    serveFile(exchange);
                    return true;
                }
                return false;
            }
        });

        next.handleRequest(exchange);
    }

    private void serveFile(final HttpServerExchange exchange) {
        String fileName = file.toString();
        int index = fileName.lastIndexOf(".");
        if(index > 0) {
            String contentType = mimeMappings.getMimeType(fileName.substring(index + 1));
            if(contentType != null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
            }
        }
        exchange.dispatch(new Runnable() {
            @Override
            public void run() {
                final FileChannel fileChannel;
                try {
                    try {
                        fileChannel = FileChannel.open(file, StandardOpenOption.READ);
                    } catch (FileNotFoundException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        exchange.endExchange();
                        return;
                    }
                } catch (IOException e) {
                    UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                    exchange.endExchange();
                    return;
                }
                long size;
                try {
                    size = Files.size(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, size);
                final StreamSinkChannel response = exchange.getResponseChannel();
                exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                    @Override
                    public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                        IoUtils.safeClose(fileChannel);
                        nextListener.proceed();
                    }
                });

                try {
                    log.tracef("Serving file %s (blocking)", fileChannel);
                    Channels.transferBlocking(response, fileChannel, 0, Files.size(file));
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
        Handlers.handlerNotNull(next);
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
            this.responseCodes = new HashSet<>(responseCodes);
        }
        return this;
    }

    public FileErrorPageHandler setResponseCodes(final Integer... responseCodes) {
        this.responseCodes = new HashSet<>(Arrays.asList(responseCodes));
        return this;
    }

    public Path getFile() {
        return file;
    }

    public FileErrorPageHandler setFile(final Path file) {
        this.file = file;
        return this;
    }

    @Override
    public String toString() {
        return "response-codes( file='" + file.toString() + "', response-codes={ " + responseCodes.stream().map(s -> s.toString()).collect(Collectors.joining(", ")) + " } )";
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "error-file";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("file", String.class);
            params.put("response-codes", Integer[].class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return new HashSet<>(Arrays.asList(new String[]{"file", "response-codes"}));
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper((String)config.get("file"), (Integer[]) config.get("response-codes"));
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final String file;
        private final Integer[] responseCodes;

        private Wrapper(String file, Integer[] responseCodes) {
            this.file = file;
            this.responseCodes = responseCodes;
        }


        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new FileErrorPageHandler(handler, Paths.get(file), responseCodes);
        }
    }
}
