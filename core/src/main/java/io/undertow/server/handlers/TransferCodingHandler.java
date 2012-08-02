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

package io.undertow.server.handlers;

import java.io.IOException;

import io.undertow.UndertowLogger;
import io.undertow.server.ChannelWrapper;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ChunkedStreamSinkChannel;
import io.undertow.util.Headers;
import org.xnio.ChannelListeners;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author Stuart Douglas
 */
public class TransferCodingHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        if (!exchange.isHttp11() && !exchange.getRequestHeaders().contains(Headers.CONNECTION)) {
            //we do not want to wrap the channel if this is not HTTP/1.1
            //or if a Connection: header has been specified
            //TODO: Connection: close and Connection: upgrade mean we do not want the chunked stream
            //is there a Connection: option that would still allow it
            HttpHandlers.executeHandler(next, exchange, completionHandler);
        } else {
            final TransferCodingChannelWrapper wrapper = new TransferCodingChannelWrapper(completionHandler);
            exchange.addResponseWrapper(wrapper);
            HttpHandlers.executeHandler(next, exchange, wrapper);
        }
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

    private static class TransferCodingChannelWrapper implements ChannelWrapper<StreamSinkChannel>, HttpCompletionHandler {

        private volatile StreamSinkChannel wrapped = null;
        private final HttpCompletionHandler delegate;

        private TransferCodingChannelWrapper(final HttpCompletionHandler delegate) {
            this.delegate = delegate;
        }


        @Override
        public StreamSinkChannel wrap(final StreamSinkChannel channel, final HttpServerExchange exchange) {
            if (exchange.getResponseHeaders().contains(Headers.CONTENT_LENGTH)) {
                return channel; //TODO: fixed length channel
            } else {
                exchange.getResponseHeaders().add(Headers.TRANSFER_ENCODING, Headers.CHUNKED);
                return wrapped = new ChunkedStreamSinkChannel(channel, false, false, exchange.getBufferPool());
            }
        }

        @Override
        public void handleComplete() {
            if (wrapped != null) {
                try {
                    wrapped.shutdownWrites();
                    if(!wrapped.flush()) {
                        wrapped.getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, ChannelListeners.closingChannelExceptionHandler()));
                    }
                } catch (IOException e) {
                    UndertowLogger.REQUEST_LOGGER.ioExceptionClosingChannel(e);
                    delegate.handleComplete();
                }
            }
            //note that we do not delegate by default, as this will close the channel
            //TODO: what other clean up do we have to do here?
        }
    }


}
