/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

package io.undertow.client;

import io.undertow.util.Protocols;
import org.xnio.OptionMap;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Emanuel Muckenhuber
 */
abstract class HttpRequestQueue {

    /**
     * Create the queueing strategy.
     *
     * @param connection the http client connection
     * @param options the connection options
     * @return the queueing strategy
     */
    static HttpRequestQueue create(HttpClientConnectionImpl connection, OptionMap options) {
        final boolean http11 = Protocols.HTTP_1_1.equals(options.get(HttpClientOptions.PROTOCOL, Protocols.HTTP_1_1));
        final boolean pipeline = options.get(HttpClientOptions.HTTP_PIPELINING, false);
        if(http11 && pipeline) {
            return new PipelineStrategy(connection);
        } else {
            return new SingleActiveStrategy(connection);
        }
    }

    private final HttpClientConnectionImpl connection;
    protected HttpRequestQueue(final HttpClientConnectionImpl connection) {
        this.connection = connection;
    }

    /**
     * Add a new request.
     *
     * @param request the request
     */
    abstract void addNewRequest(PendingHttpRequest request);

    /**
     * Notify when the request was sent.
     *
     * @param request the request
s     */
    abstract void requestSent(PendingHttpRequest request);

    /**
     * Notify when the response processing completed.
     *
     * @param request the request
     */
    abstract void requestCompleted(PendingHttpRequest request);

    /**
     * Flag indicating whether this queue supports pipelining requests.
     *
     * @return {@code true} if pipelining is supported, {@code false} otherwise
     */
    abstract boolean supportsPipelining();

    /**
     * Start sending the request.
     *
     * @param request the request
     * @param fromCallback from a callback
     */
    protected void sendRequest(PendingHttpRequest request, boolean fromCallback) {
        connection.doSendRequest(request, fromCallback);
    }

    /**
     * Start reading the response.
     *
     * @param request the request
     */
    protected void readResponse(PendingHttpRequest request) {
        connection.doReadResponse(request);
    }

    static class SingleActiveStrategy extends HttpRequestQueue {

        private final Queue<PendingHttpRequest> requestQueue = new ConcurrentLinkedQueue<PendingHttpRequest>();
        SingleActiveStrategy(final HttpClientConnectionImpl connection) {
            super(connection);
        }

        @Override
        void addNewRequest(final PendingHttpRequest request) {
            requestQueue.add(request);
            if(requestQueue.peek() == request) {
                sendRequest(request, false);
            }
        }

        @Override
        void requestSent(final PendingHttpRequest request) {
            assert request == requestQueue.peek();
            readResponse(request);
        }

        @Override
        void requestCompleted(final PendingHttpRequest request) {
            final PendingHttpRequest completed = requestQueue.poll();
            assert completed == request;
            final PendingHttpRequest send = requestQueue.peek();
            if(send != null) {
                sendRequest(send, true);
            }
        }

        @Override
        boolean supportsPipelining() {
            return false;
        }
    }

    /**
     * Try to pipeline request as soon as the request was written.
     */
    static class PipelineStrategy extends HttpRequestQueue {

        private final Queue<PendingHttpRequest> sendQueue = new ConcurrentLinkedQueue<PendingHttpRequest>();
        private final Queue<PendingHttpRequest> responseQueue = new ConcurrentLinkedQueue<PendingHttpRequest>();
        PipelineStrategy(final HttpClientConnectionImpl connection) {
            super(connection);
        }

        @Override
        void addNewRequest(final PendingHttpRequest request) {
            // TODO replace all these operations with proper thread safe ones
            sendQueue.add(request);
            if(sendQueue.peek() == request) {
                sendRequest(request, false);
            }
        }

        @Override
        void requestSent(final PendingHttpRequest request) {
            final PendingHttpRequest active = sendQueue.poll();
            assert active == request;
            responseQueue.add(active);
            if(responseQueue.peek() == active) {
                readResponse(active);
            }
            // Only pipeline for idempotent requests
            if(request.allowPipeline()) {
                final PendingHttpRequest send = sendQueue.peek();
                if(send != null) {
                    sendRequest(send, true);
                }
            }
        }

        @Override
        void requestCompleted(final PendingHttpRequest request) {
            final PendingHttpRequest completed = responseQueue.poll();
            assert completed == request;
            final PendingHttpRequest read = responseQueue.peek();
            if(read != null) {
                readResponse(read);
            }
            if(! request.allowPipeline()) {
                final PendingHttpRequest send = sendQueue.peek();
                if(send != null) {
                    sendRequest(send, true);
                }
            }
        }

        @Override
        boolean supportsPipelining() {
            return true;
        }
    }

}
