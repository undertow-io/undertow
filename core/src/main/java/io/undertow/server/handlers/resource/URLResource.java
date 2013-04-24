package io.undertow.server.handlers.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;
import org.xnio.IoUtils;

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
    public String getLastModifiedString() {
        return DateUtils.toDateString(getLastModified());
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
        Path file = getFile();
        if(file != null) {
            return Files.isDirectory(file);
        }
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

        class ServerTask implements Runnable, IoCallback {

            private InputStream inputStream;
            private byte[] buffer;
            private Sender sender;

            @Override
            public void run() {
                if (inputStream == null) {
                    try {
                        inputStream = url.openStream();
                    } catch (IOException e) {
                        exchange.setResponseCode(500);
                        return;
                    }
                    buffer = new byte[1024];//TODO: we should be pooling these
                    sender = exchange.getResponseSender();
                }
                try {
                    int res = inputStream.read(buffer);
                    if (res == -1) {
                        //we are done, just return
                        sender.close();
                        return;
                    }
                    sender.send(ByteBuffer.wrap(buffer, 0, res), this);
                } catch (IOException e) {
                    onException(exchange, sender, e);
                }

            }

            @Override
            public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                if (exchange.isInIoThread()) {
                    exchange.dispatch(this);
                } else {
                    run();
                }
            }

            @Override
            public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                IoUtils.safeClose(inputStream);
                if (!exchange.isResponseStarted()) {
                    exchange.setResponseCode(500);
                }
                exchange.endExchange();
            }
        }

        ServerTask serveTask = new ServerTask();
        if (exchange.isInIoThread()) {
            exchange.dispatch(serveTask);
        } else {
            serveTask.run();
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

    @Override
    public String getCacheKey() {
        return url.toString();
    }

    @Override
    public Path getFile() {
        if(url.getProtocol().equals("file")) {
            try {
                return Paths.get(url.toURI());
            } catch (URISyntaxException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public URL getUrl() {
        return url;
    }
}
