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

package io.undertow.server.handlers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResponseCommitListener;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.util.ConduitFactory;
import io.undertow.util.StatusCodes;

/**
 * Handler for requests that require 100-continue responses. If an attempt is made to read from the source
 * channel then a 100 continue response is sent.
 *
 * @author Stuart Douglas
 */
public class HttpContinueReadHandler implements HttpHandler {

    private static final ConduitWrapper<StreamSourceConduit> WRAPPER = new ConduitWrapper<StreamSourceConduit>() {
        @Override
        public StreamSourceConduit wrap(final ConduitFactory<StreamSourceConduit> factory, final HttpServerExchange exchange) {
            if (exchange.isRequestChannelAvailable() && !exchange.isResponseStarted()) {
                return new ContinueConduit(factory.create(), exchange);
            }
            return factory.create();
        }
    };

    private final HttpHandler handler;

    public HttpContinueReadHandler(final HttpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (HttpContinue.requiresContinueResponse(exchange)) {
            exchange.addRequestWrapper(WRAPPER);
            exchange.addResponseCommitListener(ContinueResponseCommitListener.INSTANCE);
        }
        handler.handleRequest(exchange);
    }

    private enum ContinueResponseCommitListener implements ResponseCommitListener {
        INSTANCE;

        @Override
        public void beforeCommit(HttpServerExchange exchange) {
            //we are writing the response, and have not read the request then we mark this as non-persistent
            if (!HttpContinue.isContinueResponseSent(exchange)) {
                exchange.setPersistent(false);
                //we also kill the request channel, because it is unusable now
                if (!exchange.isRequestComplete()) {
                    exchange.getConnection().terminateRequestChannel(exchange);
                } else {
                    Connectors.terminateRequest(exchange);
                }
            }
        }
    }

    private static final class ContinueConduit extends AbstractStreamSourceConduit<StreamSourceConduit> implements StreamSourceConduit {

        private boolean sent = false;
        private HttpContinue.ContinueResponseSender response = null;
        private final HttpServerExchange exchange;


        protected ContinueConduit(final StreamSourceConduit next, final HttpServerExchange exchange) {
            super(next);
            this.exchange = exchange;
        }

        @Override
        public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
            if (exchange.getStatusCode() == StatusCodes.EXPECTATION_FAILED) {
                //rejected
                Connectors.terminateRequest(exchange);
                return -1;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                if (!response.send()) {
                    return 0;
                }
                response = null;
            }
            return super.transferTo(position, count, target);
        }

        @Override
        public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
            if (exchange.getStatusCode() == StatusCodes.EXPECTATION_FAILED) {
                //rejected
                Connectors.terminateRequest(exchange);
                return -1;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                if (!response.send()) {
                    return 0;
                }
                response = null;
            }
            return super.transferTo(count, throughBuffer, target);
        }

        @Override
        public int read(final ByteBuffer dst) throws IOException {
            if (exchange.getStatusCode() == StatusCodes.EXPECTATION_FAILED) {
                //rejected
                Connectors.terminateRequest(exchange);
                return -1;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                if (!response.send()) {
                    return 0;
                }
                response = null;
            }
            return super.read(dst);
        }

        @Override
        public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
            if (exchange.getStatusCode() == StatusCodes.EXPECTATION_FAILED) {
                //rejected
                Connectors.terminateRequest(exchange);
                return -1;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                if (!response.send()) {
                    return 0;
                }
                response = null;
            }
            return super.read(dsts, offs, len);
        }

        @Override
        public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
            if (exchange.getStatusCode() == StatusCodes.EXPECTATION_FAILED) {
                //rejected
                return;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            long exitTime = System.currentTimeMillis() + timeUnit.toMillis(time);
            if (response != null) {
                while (!response.send()) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime > exitTime) {
                        return;
                    }
                    response.awaitWritable(exitTime - currentTime, TimeUnit.MILLISECONDS);
                }
                response = null;
            }

            long currentTime = System.currentTimeMillis();
            super.awaitReadable(exitTime - currentTime, TimeUnit.MILLISECONDS);
        }

        @Override
        public void awaitReadable() throws IOException {
            if (exchange.getStatusCode() == StatusCodes.EXPECTATION_FAILED) {
                //rejected
                return;
            }
            if (!sent) {
                sent = true;
                response = HttpContinue.createResponseSender(exchange);
            }
            if (response != null) {
                while (!response.send()) {
                    response.awaitWritable();
                }
                response = null;
            }
            super.awaitReadable();
        }
    }
}
