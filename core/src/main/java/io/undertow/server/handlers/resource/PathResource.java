package io.undertow.server.handlers.resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;

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
        return file.getFileName().toString();
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
                resources.add(new PathResource(child, manager, path + File.separator + child.getFileName().toString()));
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
        try {
            serveImpl(sender, exchange, 0, Files.size(file), callback);
        } catch (IOException e) {
            callback.onException(exchange, sender, e);
        }
    }
    @Override
    public void serveRange(final Sender sender, final HttpServerExchange exchange, final long start, final long end, final IoCallback callback) {
        serveImpl(sender, exchange, start, end + 1, callback);

    }

    private void serveImpl(final Sender sender, final HttpServerExchange exchange, final long start, final long end, final IoCallback<Sender> callback) {
        RandomAccessFile fileChannel;
        try {
            fileChannel = new RandomAccessFile(file.toFile(), "r");
            sender.transferFrom(fileChannel, start, end - start , callback);
        } catch (FileNotFoundException e) {
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            callback.onException(exchange, sender, e);
        }
    }

    @Override
    public Long getContentLength() {
        try {
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
