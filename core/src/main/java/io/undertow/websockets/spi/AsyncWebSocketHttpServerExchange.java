package io.undertow.websockets.spi;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.xnio.ChannelListener;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
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

/**
 * @author Stuart Douglas
 */
public class AsyncWebSocketHttpServerExchange implements WebSocketHttpExchange {

    private final HttpServerExchange exchange;
    private Sender sender;

    public AsyncWebSocketHttpServerExchange(final HttpServerExchange exchange) {
        this.exchange = exchange;
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
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        for (final HttpString header : exchange.getRequestHeaders().getHeaderNames()) {
            headers.put(header.toString(), new ArrayList<String>(exchange.getRequestHeaders().get(header)));
        }
        return Collections.unmodifiableMap(headers);
    }

    @Override
    public String getResponseHeader(final String headerName) {
        return exchange.getResponseHeaders().getFirst(HttpString.tryFromString(headerName));
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        for (final HttpString header : exchange.getResponseHeaders().getHeaderNames()) {
            headers.put(header.toString(), new ArrayList<String>(exchange.getResponseHeaders().get(header)));
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
        final FutureResult<Void> future = new FutureResult<Void>();
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
        final Pooled<ByteBuffer> pooled = exchange.getConnection().getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getResource();
        final StreamSourceChannel channel = exchange.getRequestChannel();
        int res;
        for (; ; ) {
            try {
                res = channel.read(buffer);
                if (res == -1) {
                    return new FinishedIoFuture<byte[]>(data.toByteArray());
                } else if (res == 0) {
                    //callback
                    final FutureResult<byte[]> future = new FutureResult<byte[]>();
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
                final FutureResult<byte[]> future = new FutureResult<byte[]>();
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
        return exchange.getRequestURI() + exchange.getQueryString();
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        return exchange.getConnection().getBufferPool();
    }

    @Override
    public String getQueryString() {
        return exchange.getQueryString();
    }

    @Override
    public Object getSession() {
        return null;
    }

    @Override
    public Map<String, List<String>> getRequestParameters() {
        Map<String, List<String>> params = new HashMap<String, List<String>>();
        for (Map.Entry<String, Deque<String>> param : exchange.getQueryParameters().entrySet()) {
            params.put(param.getKey(), new ArrayList<String>(param.getValue()));
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
}
