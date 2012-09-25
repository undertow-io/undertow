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

package io.undertow.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.server.httpparser.HttpExchangeBuilder;
import io.undertow.server.httpparser.HttpParser;
import io.undertow.server.httpparser.ParseState;
import io.undertow.util.GatedStreamSinkChannel;
import io.undertow.util.HeaderMap;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;

import static org.xnio.IoUtils.safeClose;

/**
 * Listener which reads requests and headers off of an HTTP stream.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpReadListener implements ChannelListener<PushBackStreamChannel> {


    private final StreamSinkChannel responseChannel;

    private volatile ParseState state;
    private volatile HttpExchangeBuilder builder;

    private final HttpServerConnection connection;

    private volatile int read = 0;
    private final int maxRequestSize;


    HttpReadListener(final StreamSinkChannel responseChannel, final HttpServerConnection connection) {
        this.responseChannel = responseChannel;
        this.connection = connection;
        maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
    }

    public void handleEvent(final PushBackStreamChannel channel) {
        final Pooled<ByteBuffer> pooled = connection.getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getResource();
        buffer.clear();
        boolean free = true;
        try {
            final int res;
            try {
                res = channel.read(buffer);
            } catch (IOException e) {
                if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                    UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException");
                }
                safeClose(channel);
                return;
            }
            if (res == 0) {
                channel.resumeReads();
                return;
            }
            if (res == -1) {
                try {
                    channel.shutdownReads();
                    final StreamSinkChannel responseChannel = this.responseChannel;
                    responseChannel.shutdownWrites();
                    // will return false if there's a response queued ahead of this one, so we'll set up a listener then
                    if (!responseChannel.flush()) {
                        responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, null));
                        responseChannel.resumeWrites();
                    }
                } catch (IOException e) {
                    if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                        UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                    }
                    // fuck it, it's all ruined
                    IoUtils.safeClose(channel);
                    return;
                }
                return;
            }
            //TODO: we need to handle parse errors
            buffer.flip();
            if (state == null) {
                state = new ParseState();
                builder = new HttpExchangeBuilder();
            }
            int remaining = HttpParser.INSTANCE.handle(buffer, res, state, builder);
            if (remaining > 0) {
                free = false;
                channel.unget(pooled);
            }
            int total = read + res - remaining;
            read = total;
            if (read > maxRequestSize) {
                UndertowLogger.REQUEST_LOGGER.requestHeaderWasTooLarge(connection.getPeerAddress(), maxRequestSize);
                IoUtils.safeClose(connection);
                return;
            }

            if (state.isComplete()) {
                // we remove ourselves as the read listener from the channel;
                // if the http handler doesn't set any then reads will suspend, which is the right thing to do
                channel.getReadSetter().set(null);
                channel.suspendReads();
                final StreamSinkChannel ourResponseChannel = this.responseChannel;
                final Object permit = new Object();
                final StreamSinkChannel nextRequestResponseChannel;
                final Runnable responseTerminateAction;
                if (connection.getMaxConcurrentRequests() > 1) {
                    GatedStreamSinkChannel gatedStreamSinkChannel = new GatedStreamSinkChannel(connection.getChannel(), permit, false, true);
                    nextRequestResponseChannel = gatedStreamSinkChannel;
                    responseTerminateAction = new ResponseTerminateAction(gatedStreamSinkChannel, permit);
                } else {
                    nextRequestResponseChannel = connection.getChannel();
                    responseTerminateAction = null;
                }
                final HeaderMap requestHeaders = builder.getHeaders();
                final HeaderMap responseHeaders = new HeaderMap();
                final Map<String, Deque<String>> parameters = builder.getQueryParameters();
                final String method = builder.getMethod();
                final String protocol = builder.getProtocol();

                final StartNextRequestAction startNextRequestAction = new StartNextRequestAction(channel, nextRequestResponseChannel, connection);

                final HttpServerExchange httpServerExchange = new HttpServerExchange(connection, requestHeaders, responseHeaders, parameters, method, protocol, channel, ourResponseChannel, startNextRequestAction, responseTerminateAction);
                httpServerExchange.putAttachment(UndertowOptions.ATTACHMENT_KEY, connection.getUndertowOptions());
                try {
                    httpServerExchange.setRequestScheme("http"); //todo: determine if this is https
                    httpServerExchange.setRequestURI(builder.getFullPath());
                    httpServerExchange.setRelativePath(builder.getRelativePath());
                    httpServerExchange.setRequestPath(builder.getRelativePath());
                    httpServerExchange.setQueryString(builder.getQueryString());

                    state = null;
                    builder = null;
                    connection.getRootHandler().handleRequest(httpServerExchange, new CompletionHandler(httpServerExchange, startNextRequestAction));

                } catch (Throwable t) {
                    //TODO: we should attempt to return a 500 status code in this situation
                    UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(t);
                    IoUtils.safeClose(nextRequestResponseChannel);
                    IoUtils.safeClose(channel);
                }
            }
        } finally {
            if (free) pooled.free();
        }
    }

    /**
     * Action that starts the next request
     */
    private static class StartNextRequestAction implements Runnable {

        private volatile PushBackStreamChannel channel;
        private volatile StreamSinkChannel nextRequestResponseChannel;
        private volatile HttpServerConnection connection;

        /**
         * maintains the current state.
         * 0= request has not finished, completion handler has not run
         * 1=next request started
         * 2=previous request finished, but request not started
         * 3=completion handler run, but next request not started
         */
        @SuppressWarnings("unused")
        private volatile int state = 0;
        private static final AtomicIntegerFieldUpdater<StartNextRequestAction> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(StartNextRequestAction.class, "state");


        public StartNextRequestAction(final PushBackStreamChannel channel, final StreamSinkChannel nextRequestResponseChannel, final HttpServerConnection connection) {
            this.channel = channel;
            this.nextRequestResponseChannel = nextRequestResponseChannel;
            this.connection = connection;
        }

        /**
         * This method is called when the
         */
        public void run() {
            int state;
            do {
                state = stateUpdater.get(this);
                if (state == 3) {
                    //we start unconditionally
                    startNextRequest();
                    return;
                } else if (state == 0 && connection.startRequest()) {
                    startNextRequest();
                    return;
                }
            } while (!stateUpdater.compareAndSet(this, state, 2));
        }

        private void startNextRequest() {
            stateUpdater.set(this, 1);
            final PushBackStreamChannel channel = this.channel;
            channel.getReadSetter().set(new HttpReadListener(nextRequestResponseChannel, connection));
            channel.getReadThread().execute(new Runnable() {
                @Override
                public void run() {
                    channel.resumeReads();
                }
            });
            nextRequestResponseChannel = null;
            connection = null;
            this.channel = null;
        }

        public void completionHandler() {
            int state;
            do {
                state = stateUpdater.get(this);
                if (state == 1) {
                    return;
                }
                if (state == 2) {
                    //we start unconditionally
                    startNextRequest();
                    return;
                }
            } while (!stateUpdater.compareAndSet(this, state, 3));
        }
    }

    private static class ResponseTerminateAction implements Runnable {
        private volatile GatedStreamSinkChannel nextRequestResponseChannel;
        private volatile Object permit;

        public ResponseTerminateAction(GatedStreamSinkChannel nextRequestResponseChannel, Object permit) {
            this.nextRequestResponseChannel = nextRequestResponseChannel;
            this.permit = permit;
        }

        public void run() {
            nextRequestResponseChannel.openGate(permit);
            nextRequestResponseChannel = null;
            permit = null;
        }
    }

    private static class CompletionHandler extends AtomicBoolean implements HttpCompletionHandler {
        private final HttpServerExchange httpServerExchange;
        private final StartNextRequestAction startNextRequestAction;

        public CompletionHandler(final HttpServerExchange httpServerExchange, final StartNextRequestAction startNextRequestAction) {
            this.httpServerExchange = httpServerExchange;
            this.startNextRequestAction = startNextRequestAction;
        }

        public void handleComplete() {
            if (!compareAndSet(false, true)) {
                return;
            }
            try {
                httpServerExchange.cleanup();
            } finally {
                //mark this request as finished to allow the next request to run
                startNextRequestAction.completionHandler();
            }
        }
    }
}
