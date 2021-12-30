package io.undertow.server.handlers.resource;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;
import org.xnio.IoUtils;
import io.undertow.connector.PooledByteBuffer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A path resource
 *
 * @author Stuart Douglas
 */
public class PathResource implements RangeAwareResource {

    private final Path file;
    private final String path;
    private final ETag eTag;
    private final PathResourceManager manager;

    public PathResource(final Path file, final PathResourceManager manager, String path, ETag eTag) {
        this.file = file;
        this.path = path;
        this.manager = manager;
        this.eTag = eTag;
    }

    public PathResource(final Path file, final PathResourceManager manager, String path) {
        this(file, manager, path, null);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Date getLastModified() {
        try {
            if (Files.isSymbolicLink(file) && Files.notExists(file)) {
                return null;
            }
            return new Date(Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getLastModifiedString() {
        return DateUtils.toDateString(getLastModified());
    }

    @Override
    public ETag getETag() {
        return eTag;
    }

    @Override
    public String getName() {
        if( file.getFileName() != null ) {
            return file.getFileName().toString();
        }
        return file.toString();
    }

    @Override
    public boolean isDirectory() {
        return Files.isDirectory(file);
    }

    @Override
    public List<Resource> list() {
        final List<Resource> resources = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(file)) {
            for (Path child : stream) {
                resources.add(new PathResource(child, manager, path + file.getFileSystem().getSeparator() + child.getFileName().toString()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return resources;
    }

    @Override
    public String getContentType(final MimeMappings mimeMappings) {
        final String fileName = file.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        if (index != -1 && index != fileName.length() - 1) {
            return mimeMappings.getMimeType(fileName.substring(index + 1));
        }
        return null;
    }

    @Override
    public void serve(final Sender sender, final HttpServerExchange exchange, final IoCallback callback) {
        serveImpl(sender, exchange, -1, -1, callback, false);
    }
    @Override
    public void serveRange(final Sender sender, final HttpServerExchange exchange, final long start, final long end, final IoCallback callback) {
        serveImpl(sender, exchange, start, end, callback, true);

    }
    private void serveImpl(final Sender sender, final HttpServerExchange exchange, final long start, final long end, final IoCallback callback, final boolean range) {


        abstract class BaseFileTask implements Runnable {
            protected volatile FileChannel fileChannel;

            protected boolean openFile() {
                try {
                    fileChannel = FileChannel.open(file, StandardOpenOption.READ);
                    if(range) {
                        fileChannel.position(start);
                    }
                } catch (NoSuchFileException e) {
                    exchange.setStatusCode(StatusCodes.NOT_FOUND);
                    callback.onException(exchange, sender, e);
                    return false;
                } catch (IOException e) {
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    callback.onException(exchange, sender, e);
                    return false;
                }
                return true;
            }
        }

        class ServerTask extends BaseFileTask implements IoCallback {

            private PooledByteBuffer pooled;

            long remaining = end - start + 1;

            @Override
            public void run() {
                if(range && remaining == 0) {
                    //we are done
                    if (pooled != null) {
                        pooled.close();
                        pooled = null;
                    }
                    IoUtils.safeClose(fileChannel);
                    callback.onComplete(exchange, sender);
                    return;
                }
                if (fileChannel == null) {
                    if (!openFile()) {
                        return;
                    }
                    pooled = exchange.getConnection().getByteBufferPool().allocate();
                }
                if (pooled != null) {
                    ByteBuffer buffer = pooled.getBuffer();
                    try {
                        buffer.clear();
                        int res = fileChannel.read(buffer);
                        if (res == -1) {
                            //we are done
                            pooled.close();
                            IoUtils.safeClose(fileChannel);
                            callback.onComplete(exchange, sender);
                            return;
                        }
                        buffer.flip();
                        if(range) {
                            if(buffer.remaining() > remaining) {
                                buffer.limit((int) (buffer.position() + remaining));
                            }
                            remaining -= buffer.remaining();
                        }
                        sender.send(buffer, this);
                    } catch (IOException e) {
                        onException(exchange, sender, e);
                    }
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
                UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                if (pooled != null) {
                    pooled.close();
                    pooled = null;
                }
                IoUtils.safeClose(fileChannel);
                if (!exchange.isResponseStarted()) {
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                }
                callback.onException(exchange, sender, exception);
            }
        }

        class TransferTask extends BaseFileTask {

            @Override
            public void run() {
                if (!openFile()) {
                    return;
                }
                sender.transferFrom(fileChannel, new IoCallback() {
                    @Override
                    public void onComplete(HttpServerExchange exchange, Sender sender) {
                        try {
                            IoUtils.safeClose(fileChannel);
                        } finally {
                            callback.onComplete(exchange, sender);
                        }
                    }

                    @Override
                    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                        try {
                            IoUtils.safeClose(fileChannel);
                        } finally {
                            callback.onException(exchange, sender, exception);
                        }
                    }
                });
            }
        }
        BaseFileTask task;
        try {
            task = manager.getTransferMinSize() > Files.size(file) || range ? new ServerTask() : new TransferTask();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (exchange.isInIoThread()) {
            exchange.dispatch(task);
        } else {
            task.run();
        }
    }

    @Override
    public Long getContentLength() {
        try {
            if (Files.isSymbolicLink(file) && Files.notExists(file)) {
                return null;
            }
            return Files.size(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getCacheKey() {
        return file.toString();
    }

    @Override
    public File getFile() {
        return file.toFile();
    }

    @Override
    public Path getFilePath() {
        return file;
    }

    @Override
    public File getResourceManagerRoot() {
        return manager.getBasePath().toFile();
    }

    @Override
    public Path getResourceManagerRootPath() {
        return manager.getBasePath();
    }

    @Override
    public URL getUrl() {
        try {
            return file.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isRangeSupported() {
        return true;
    }

}
