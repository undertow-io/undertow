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

package io.undertow.websockets.spi;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.websockets.core.WebSocketChannel;
import org.xnio.ChannelListener;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.channels.StreamSourceChannel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author Stuart Douglas
 */
public class AsyncWebSocketHttpServerExchange implements WebSocketHttpExchange {

    private final HttpServerExchange exchange;
    private Sender sender;
    private final Set<WebSocketChannel> peerConnections;

    public AsyncWebSocketHttpServerExchange(final HttpServerExchange exchange, Set<WebSocketChannel> peerConnections) {
        this.exchange = exchange;
        this.peerConnections = peerConnections;
    }


    @Override
    public <T> void putAttachment(final AttachmentKey<T> key, final T value) {
        exchange.putAttachment(key, value);
    }

    @Override
    public <T> T getAttachment(final AttachmentKey<T> key) {
        return exchange.getAttachment(key);
    }

    @Override
    public String getRequestHeader(final String headerName) {
        return exchange.getRequestHeaders().getFirst(HttpString.tryFromString(headerName));
    }

    @Override
    public Map<String, List<String>> getRequestHeaders() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (final HttpString header : exchange.getRequestHeaders().getHeaderNames()) {
            headers.put(header.toString(), new ArrayList<>(exchange.getRequestHeaders().get(header)));
        }
        return Collections.unmodifiableMap(headers);
    }

    @Override
    public String getResponseHeader(final String headerName) {
        return exchange.getResponseHeaders().getFirst(HttpString.tryFromString(headerName));
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        for (final HttpString header : exchange.getResponseHeaders().getHeaderNames()) {
            headers.put(header.toString(), new ArrayList<>(exchange.getResponseHeaders().get(header)));
        }
        return Collections.unmodifiableMap(headers);
    }

    @Override
    public void setResponseHeaders(final Map<String, List<String>> headers) {
        HeaderMap map = exchange.getRequestHeaders();
        map.clear();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            map.addAll(HttpString.tryFromString(header.getKey()), header.getValue());
        }
    }

    @Override
    public void setResponseHeader(final String headerName, final String headerValue) {
        exchange.getResponseHeaders().put(HttpString.tryFromString(headerName), headerValue);
    }

    @Override
    public void upgradeChannel(final HttpUpgradeListener upgradeCallback) {
        exchange.upgradeChannel(upgradeCallback);
    }

    @Override
    public IoFuture<Void> sendData(final ByteBuffer data) {
        if (sender == null) {
            this.sender = exchange.getResponseSender();
        }
        final FutureResult<Void> future = new FutureResult<>();
        sender.send(data, new IoCallback() {
            @Override
            public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                future.setResult(null);
            }

            @Override
            public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                future.setException(exception);

            }
        });
        return future.getIoFuture();
    }

    @Override
    public IoFuture<byte[]> readRequestData() {
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        final PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
        final ByteBuffer buffer = pooled.getBuffer();
        final StreamSourceChannel channel = exchange.getRequestChannel();
        int res;
        for (; ; ) {
            try {
                res = channel.read(buffer);
                if (res == -1) {
                    return new FinishedIoFuture<>(data.toByteArray());
                } else if (res == 0) {
                    //callback
                    final FutureResult<byte[]> future = new FutureResult<>();
                    channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                        @Override
                        public void handleEvent(final StreamSourceChannel channel) {
                            int res;
                            try {
                                res = channel.read(buffer);
                                if (res == -1) {
                                    future.setResult(data.toByteArray());
                                    channel.suspendReads();
                                    return;
                                } else if (res == 0) {
                                    return;
                                } else {
                                    buffer.flip();
                                    while (buffer.hasRemaining()) {
                                        data.write(buffer.get());
                                    }
                                    buffer.clear();
                                }

                            } catch (IOException e) {
                                future.setException(e);
                            }
                        }
                    });
                    channel.resumeReads();
                    return future.getIoFuture();
                } else {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        data.write(buffer.get());
                    }
                    buffer.clear();
                }

            } catch (IOException e) {
                final FutureResult<byte[]> future = new FutureResult<>();
                future.setException(e);
                return future.getIoFuture();
            }
        }


    }

    @Override
    public void endExchange() {
        exchange.endExchange();
    }

    @Override
    public void close() {
        try {
            exchange.endExchange();
        } finally {
            IoUtils.safeClose(exchange.getConnection());
        }
    }

    @Override
    public String getRequestScheme() {
        return exchange.getRequestScheme();
    }

    @Override
    public String getRequestURI() {
        String q = exchange.getQueryString();
        if (q == null || q.isEmpty()) {
            return exchange.getRequestURI();
        } else {
            return exchange.getRequestURI() + "?" + q;
        }
    }

    @Override
    public ByteBufferPool getBufferPool() {
        return exchange.getConnection().getByteBufferPool();
    }

    @Override
    public String getQueryString() {
        return exchange.getQueryString();
    }

    @Override
    public Object getSession() {
        SessionManager sm = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        SessionConfig sessionCookieConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);
        if(sm != null && sessionCookieConfig != null) {
            return sm.getSession(exchange, sessionCookieConfig);
        }
        return null;
    }

    @Override
    public Map<String, List<String>> getRequestParameters() {
        Map<String, List<String>> params = new HashMap<>();
        for (Map.Entry<String, Deque<String>> param : exchange.getQueryParameters().entrySet()) {
            params.put(param.getKey(), new ArrayList<>(param.getValue()));
        }
        return params;
    }

    @Override
    public Principal getUserPrincipal() {
        SecurityContext sc = exchange.getSecurityContext();
        if(sc == null) {
            return null;
        }
        Account authenticatedAccount = sc.getAuthenticatedAccount();
        if(authenticatedAccount == null) {
            return null;
        }
        return authenticatedAccount.getPrincipal();
    }

    @Override
    public boolean isUserInRole(String role) {
        SecurityContext sc = exchange.getSecurityContext();
        if(sc == null) {
            return false;
        }
        Account authenticatedAccount = sc.getAuthenticatedAccount();
        if(authenticatedAccount == null) {
            return false;
        }
        return authenticatedAccount.getRoles().contains(role);
    }

    @Override
    public Set<WebSocketChannel> getPeerConnections() {
        return peerConnections;
    }

    @Override
    public OptionMap getOptions() {
        return exchange.getConnection().getUndertowOptions();
    }
}
