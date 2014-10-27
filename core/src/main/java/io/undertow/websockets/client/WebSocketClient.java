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

package io.undertow.websockets.client;

import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;

import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.http.HttpUpgrade;
import org.xnio.ssl.XnioSsl;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Web socket client.
 *
 * @author Stuart Douglas
 */
public class WebSocketClient {


    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version) {
        return connect(worker, bufferPool, optionMap, uri, version, null);
    }

    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version) {
        return connect(worker, ssl, bufferPool, optionMap, uri, version, null);
    }

    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation) {
        return connect(worker, null, bufferPool, optionMap, uri, version, clientNegotiation);
    }

    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation) {

        final FutureResult<WebSocketChannel> ioFuture = new FutureResult<>();
        final URI newUri;
        try {
            newUri = new URI(uri.getScheme().equals("wss") ? "https" : "http", uri.getUserInfo(), uri.getHost(), uri.getPort() == -1 ? (uri.getScheme().equals("wss") ? 443 : 80) : uri.getPort(), uri.getPath().isEmpty() ? "/" : uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        final WebSocketClientHandshake handshake = WebSocketClientHandshake.create(version, newUri, clientNegotiation);
        final Map<String, String> originalHeaders = handshake.createHeaders();
        originalHeaders.put(Headers.ORIGIN_STRING, uri.getHost());
        final Map<String, List<String>> headers = new HashMap<>();
        for(Map.Entry<String, String> entry : originalHeaders.entrySet()) {
            List<String> list = new ArrayList<>();
            list.add(entry.getValue());
            headers.put(entry.getKey(), list);
        }
        if (clientNegotiation != null) {
            clientNegotiation.beforeRequest(headers);
        }
        final IoFuture<? extends StreamConnection> result;
        if (ssl != null) {
            result = HttpUpgrade.performUpgrade(worker, ssl, null, newUri, headers, new ChannelListener<StreamConnection>() {
                @Override
                public void handleEvent(StreamConnection channel) {
                    WebSocketChannel result = handshake.createChannel(channel, newUri.toString(), bufferPool);
                    ioFuture.setResult(result);
                }
            }, null, optionMap, handshake.handshakeChecker(newUri, headers));
        } else {
            result = HttpUpgrade.performUpgrade(worker, null, newUri, headers, new ChannelListener<StreamConnection>() {
                @Override
                public void handleEvent(StreamConnection channel) {
                    WebSocketChannel result = handshake.createChannel(channel, newUri.toString(), bufferPool);
                    ioFuture.setResult(result);
                }
            }, null, optionMap, handshake.handshakeChecker(newUri, headers));
        }
        result.addNotifier(new IoFuture.Notifier<StreamConnection, Object>() {
            @Override
            public void notify(IoFuture<? extends StreamConnection> res, Object attachment) {
                if (res.getStatus() == IoFuture.Status.FAILED) {
                    ioFuture.setException(res.getException());
                }
            }
        }, null);
        ioFuture.addCancelHandler(new Cancellable() {
            @Override
            public Cancellable cancel() {
                result.cancel();
                return null;
            }
        });
        return ioFuture.getIoFuture();
    }


    private WebSocketClient() {

    }
}
