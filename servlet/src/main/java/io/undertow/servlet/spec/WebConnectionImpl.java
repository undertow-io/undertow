package io.undertow.servlet.spec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.WebConnection;

import org.xnio.Pool;
import org.xnio.StreamConnection;

/**
 * @author Stuart Douglas
 */
public class WebConnectionImpl implements WebConnection {

    private final UpgradeServletOutputStream outputStream;
    private final UpgradeServletInputStream inputStream;
    private final Executor ioExecutor;

    public WebConnectionImpl(final StreamConnection channel, Pool<ByteBuffer> bufferPool, Executor ioExecutor) {
        this.ioExecutor = ioExecutor;
        this.outputStream = new UpgradeServletOutputStream(channel.getSinkChannel(), ioExecutor);
        this.inputStream = new UpgradeServletInputStream(channel.getSourceChannel(), bufferPool, ioExecutor);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return inputStream;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return outputStream;
    }

    @Override
    public void close() throws Exception {
        outputStream.closeBlocking();
    }
}
