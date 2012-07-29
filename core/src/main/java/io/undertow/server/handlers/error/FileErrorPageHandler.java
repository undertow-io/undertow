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
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.FileWriteChannelListener;
import org.xnio.channels.StreamSinkChannel;

/**
 * Handler that serves up a file from disk to serve as an error page.
 *
 * This handler does not server up and response codes by default, you must configure
 * the response codes it responds to.
 *
 * @author Stuart Douglas
 */
public class FileErrorPageHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    /**
     * The response codes that this handler will handle. If this is empty then this handler will have no effect.
     */
    private volatile Set<Integer> responseCodes;

    private volatile File file;

    private volatile ExecutorService executorService;

    public FileErrorPageHandler(final File file, final ExecutorService executorService, final Integer ... responseCodes) {
        this.file = file;
        this.executorService = executorService;
        this.responseCodes = new HashSet<Integer>(Arrays.asList(responseCodes));
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        HttpHandlers.executeHandler(next, exchange, new HttpCompletionHandler() {
            @Override
            public void handleComplete() {
                Set<Integer> codes = responseCodes;
                if (!exchange.isResponseStarted() &&  codes.contains(exchange.getResponseCode())) {
                    if(!file.exists()) {
                        UndertowLogger.ROOT_LOGGER.errorPageDoesNotExist(file);
                        completionHandler.handleComplete();
                    } else  {
                        final StreamSinkChannel response = exchange.getResponseChannel();
                        try {
                            final FileWriteChannelListener listener = new FileWriteChannelListener(file, response.getWorker().getXnio(), executorService) {
                                @Override
                                protected void writeDone(final StreamSinkChannel channel) {
                                    completionHandler.handleComplete();
                                }
                            };
                            listener.setup(response);
                        } catch (IOException e) {
                            UndertowLogger.ROOT_LOGGER.errorLoadingErrorPage(e, file);
                            completionHandler.handleComplete();
                        }
                    }
                } else {
                    completionHandler.handleComplete();
                }
            }
        });
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

    public Set<Integer> getResponseCodes() {
        return Collections.unmodifiableSet(responseCodes);
    }

    public void setResponseCodes(final Set<Integer> responseCodes) {
        if(responseCodes == null) {
            this.responseCodes = Collections.emptySet();
        } else {
            this.responseCodes = new HashSet<Integer>(responseCodes);
        }
    }

    public void setResponseCodes(final Integer... responseCodes) {
        this.responseCodes = new HashSet<Integer>(Arrays.asList(responseCodes));
    }

    public File getFile() {
        return file;
    }

    public void setFile(final File file) {
        this.file = file;
    }
}
