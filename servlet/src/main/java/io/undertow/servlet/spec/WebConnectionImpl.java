package io.undertow.servlet.spec;

import java.io.IOException;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.WebConnection;

import org.xnio.channels.ConnectedStreamChannel;

/**
 * @author Stuart Douglas
 */
public class WebConnectionImpl implements WebConnection {

    private final UpgradeServletOutputStream outputStream;
    private final UpgradeServletInputStream inputStream;

    public WebConnectionImpl(final ConnectedStreamChannel channel) {
        this.outputStream = new UpgradeServletOutputStream(channel);
        this.inputStream = new UpgradeServletInputStream(channel);
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
