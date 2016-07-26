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

package io.undertow.server.handlers.sse;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.PathTemplateMatch;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Stuart Douglas
 */
public class ServerSentEventHandler implements HttpHandler {

    private static final HttpString LAST_EVENT_ID = new HttpString("Last-Event-ID");

    private final ServerSentEventConnectionCallback callback;

    private final Set<ServerSentEventConnection> connections = Collections.newSetFromMap(new ConcurrentHashMap<ServerSentEventConnection, Boolean>());

    public ServerSentEventHandler(ServerSentEventConnectionCallback callback) {
        this.callback = callback;
    }

    public ServerSentEventHandler() {
        this.callback = null;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/event-stream; charset=UTF-8");
        exchange.setPersistent(false);
        final StreamSinkChannel sink = exchange.getResponseChannel();
        if(!sink.flush()) {
            sink.getWriteSetter().set(ChannelListeners.flushingChannelListener(new ChannelListener<StreamSinkChannel>() {
                @Override
                public void handleEvent(StreamSinkChannel channel) {
                    handleConnect(channel, exchange);
                }
            }, new ChannelExceptionHandler<StreamSinkChannel>() {
                @Override
                public void handleException(StreamSinkChannel channel, IOException exception) {
                    IoUtils.safeClose(exchange.getConnection());
                }
            }));
            sink.resumeWrites();
        } else {
            exchange.dispatch(exchange.getIoThread(), new Runnable() {
                @Override
                public void run() {
                    handleConnect(sink, exchange);
                }
            });
        }
    }

    private void handleConnect(StreamSinkChannel channel, HttpServerExchange exchange) {
        UndertowLogger.REQUEST_LOGGER.debugf("Opened SSE connection to %s", exchange);
        final ServerSentEventConnection connection = new ServerSentEventConnection(exchange, channel);
        PathTemplateMatch pt = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        if(pt != null) {
            for(Map.Entry<String, String> p : pt.getParameters().entrySet()) {
                connection.setParameter(p.getKey(), p.getValue());
            }
        }
        connections.add(connection);
        connection.addCloseTask(new ChannelListener<ServerSentEventConnection>() {
            @Override
            public void handleEvent(ServerSentEventConnection channel) {
                connections.remove(connection);
            }
        });
        if(callback != null) {
            callback.connected(connection, exchange.getRequestHeaders().getLast(LAST_EVENT_ID));
        }
    }

    public Set<ServerSentEventConnection> getConnections() {
        return Collections.unmodifiableSet(connections);
    }
}
