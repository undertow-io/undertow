package io.undertow.websockets.spi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.xnio.ChannelListener;
import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

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
    public String getRequestHeader(final String headerName) {
        return exchange.getRequestHeaders().getFirst(HttpString.tryFromString(headerName));
    }

    @Override
    public Map<String, List<String>> getRequestHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
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
    public void setResponesCode(final int code) {
        exchange.setResponseCode(code);
    }

    @Override
    public void upgradeChannel(final UpgradeCallback upgradeCallback) {
        exchange.upgradeChannel(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
                upgradeCallback.handleUpgrade(exchange.getConnection().getChannel(), exchange.getConnection().getBufferPool());
            }
        });
    }

    @Override
    public IoFuture<Void> sendData(final ByteBuffer data) {
        if (sender == null) {
            this.sender = exchange.getResponseSender();
        }
        final ConcreteIoFuture<Void> future = new ConcreteIoFuture<>();
        sender.send(data, new IoCallback() {
            @Override
            public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                future.setResult(null);
            }

            @Override
            public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                future.setException(exception);

            }
        });
        return future;
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
                    final ConcreteIoFuture<byte[]> future = new ConcreteIoFuture<>();
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
                    return future;
                } else {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        data.write(buffer.get());
                    }
                    buffer.clear();
                }

            } catch (IOException e) {
                final ConcreteIoFuture<byte[]> future = new ConcreteIoFuture<>();
                future.setException(e);
                return future;
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
        return exchange.getRequestURI();
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        return exchange.getConnection().getBufferPool();
    }

    @Override
    public String getQueryString() {
        return getQueryString();
    }
}
