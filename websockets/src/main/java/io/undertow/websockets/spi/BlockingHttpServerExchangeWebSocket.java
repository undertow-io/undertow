package io.undertow.websockets.spi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import io.undertow.io.UndertowOutputStream;
import io.undertow.server.HttpServerExchange;
import org.xnio.streams.ChannelInputStream;

/**
 * @author Stuart Douglas
 */
public class BlockingHttpServerExchangeWebSocket extends AsyncHttpServerExchangeWebSocket {

    private final OutputStream out;
    private final InputStream in;

    public BlockingHttpServerExchangeWebSocket(final HttpServerExchange exchange) {
        super(exchange);
        out = new UndertowOutputStream(exchange);
        in = new ChannelInputStream(exchange.getRequestChannel());
    }

    @Override
    public void sendData(final ByteBuffer data, final WriteCallback callback) {
        while (data.hasRemaining()) {
            try {
                out.write(data.get());
            } catch (IOException e) {
                callback.error(this, e);
                return;
            }
        }
        callback.onWrite(this);
    }

    @Override
    public void readRequestData(final ReadCallback callback) {
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int r;

        try {
            while ((r = in.read(buf)) != -1) {
                data.write(buf, 0, r);
            }
        } catch (IOException e) {
            callback.error(this, e);
            return;
        }
        callback.onRead(this, data.toByteArray());
    }
}
