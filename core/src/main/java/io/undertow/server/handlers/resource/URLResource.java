package io.undertow.server.handlers.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author Stuart Douglas
 */
public class URLResource implements Resource {

    private final URL url;
    private final URLConnection connection;

    public URLResource(final URL url, final URLConnection connection) {
        this.url = url;
        this.connection = connection;
    }

    @Override
    public Date getLastModified() {
        return new Date(connection.getLastModified());
    }

    @Override
    public ETag getETag() {
        return null;
    }

    @Override
    public String getName() {
        String path = url.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 2);
        }
        int sepIndex = path.lastIndexOf("/");
        if (sepIndex != -1) {
            path = path.substring(sepIndex + 1);
        }
        return path;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public List<Resource> list() {
        return null;
    }

    @Override
    public String getContentType(final MimeMappings mimeMappings) {
        final String fileName = getName();
        int index = fileName.lastIndexOf('.');
        if (index != -1 && index != fileName.length() - 1) {
            return mimeMappings.getMimeType(fileName.substring(index + 1));
        }
        return null;
    }

    @Override
    public void serve(final HttpServerExchange exchange) {
        InputStream in = null;
        try {
            in = connection.getInputStream();
            final StreamSinkChannel responseChannel = exchange.getResponseChannel();
            byte[] buffer = new byte[1024];
            int read = 0;
            while ((read = in.read(buffer)) != -1) {
                Channels.writeBlocking(responseChannel, ByteBuffer.wrap(buffer, 0, read));
            }
        } catch (IOException e) {
            exchange.setResponseCode(500);
        } finally {
            IoUtils.safeClose(in);
            exchange.endExchange();
        }
    }

    @Override
    public Long getContentLength() {
        return (long) connection.getContentLength();
    }

    @Override
    public Resource getIndexResource(List<String> possible) {
        return null;
    }
}
