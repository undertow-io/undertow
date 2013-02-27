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

import java.util.AbstractCollection;
import java.util.Deque;

/**
 * @author Emanuel Muckenhuber
 */
abstract class HttpRequestQueueStrategy {

    /**
     * Create the queueing strategy.
     *
     * @param connection the http client connection
     * @param options the connection options
     * @return the queueing strategy
     */
    static HttpRequestQueueStrategy create(HttpClientConnectionImpl connection, OptionMap options) {
        final boolean http11 = Protocols.HTTP_1_1.equals(options.get(HttpClientOptions.PROTOCOL, Protocols.HTTP_1_1));
        final boolean pipeline = options.get(HttpClientOptions.HTTP_PIPELINING, false);
        if(http11 && pipeline) {
            return new PipelineStrategy(connection);
        } else {
            return new SingleActiveStrategy(connection);
        }
    }

    private final HttpClientConnectionImpl connection;
    protected HttpRequestQueueStrategy(final HttpClientConnectionImpl connection) {
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

    static class SingleActiveStrategy extends HttpRequestQueueStrategy {

        private final HttpRequestQueue<PendingHttpRequest> requestQueue = new HttpRequestQueueImpl<PendingHttpRequest>();
        SingleActiveStrategy(final HttpClientConnectionImpl connection) {
            super(connection);
        }

        @Override
        void addNewRequest(final PendingHttpRequest request) {
            if(requestQueue.addAndCheckFirst(request)) {
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
            final PendingHttpRequest send = requestQueue.removeAndPeekNext();
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
    static class PipelineStrategy extends HttpRequestQueueStrategy {

        private final HttpRequestQueue<PendingHttpRequest> sendQueue = new HttpRequestQueueImpl<PendingHttpRequest>();
        private final HttpRequestQueue<PendingHttpRequest> responseQueue = new HttpRequestQueueImpl<PendingHttpRequest>();
        PipelineStrategy(final HttpClientConnectionImpl connection) {
            super(connection);
        }

        @Override
        void addNewRequest(final PendingHttpRequest request) {
            if(sendQueue.addAndCheckFirst(request)) {
                sendRequest(request, false);
            }
        }

        @Override
        void requestSent(final PendingHttpRequest request) {
            final PendingHttpRequest send = sendQueue.removeAndPeekNext();
            if(responseQueue.addAndCheckFirst(request)) {
                readResponse(request);
            }
            // Only pipeline for idempotent requests
            if(send != null && request.allowPipeline()) {
                sendRequest(send, true);
            }
        }

        @Override
        void requestCompleted(final PendingHttpRequest request) {
            final PendingHttpRequest read = responseQueue.removeAndPeekNext();
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

    abstract static class HttpRequestQueue<E> extends AbstractCollection<E> implements Deque<E>, java.io.Serializable {

        /**
         * Add last and check if the element is the head of the queue.
         *
         * @param element the element to add
         * @return {@code true} if the element is the current head of the queue
         */
        abstract boolean addAndCheckFirst(E element);

        /**
         * Remove the first entry in the queue and retrieve the next one in the queue
         *
         * @return the next one in the queue, {@code null} if there is no element queued
         */
        abstract E removeAndPeekNext();

    }

}
