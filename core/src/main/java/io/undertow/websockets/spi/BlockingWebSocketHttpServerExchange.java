package io.undertow.websockets.spi;

import io.undertow.server.HttpServerExchange;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class BlockingWebSocketHttpServerExchange extends AsyncWebSocketHttpServerExchange {

    private final OutputStream out;
    private final InputStream in;

    public BlockingWebSocketHttpServerExchange(final HttpServerExchange exchange) {
        super(exchange);
        out = exchange.getOutputStream();
        in = exchange.getInputStream();
    }

    @Override
    public IoFuture<Void> sendData(final ByteBuffer data) {
        try {
            while (data.hasRemaining()) {
                out.write(data.get());
            }
            return new FinishedIoFuture<Void>(null);
        } catch (IOException e) {
            final FutureResult<Void> ioFuture = new FutureResult<Void>();
            ioFuture.setException(e);
            return ioFuture.getIoFuture();
        }
    }

    @Override
    public IoFuture<byte[]> readRequestData() {
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                data.write(buf, 0, r);
            }
            return new FinishedIoFuture<byte[]>(data.toByteArray());
        } catch (IOException e) {
            final FutureResult<byte[]> ioFuture = new FutureResult<byte[]>();
            ioFuture.setException(e);
            return ioFuture.getIoFuture();
        }
    }
}
