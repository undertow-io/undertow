/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server.handlers.encoding;

import io.undertow.UndertowLogger;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.CachingResourceManager;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.util.ImmediateConduitFactory;
import org.xnio.IoUtils;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Class that provides a way of serving pre-encoded resources.
 *
 * @author Stuart Douglas
 */
public class ContentEncodedResourceManager {


    private final Path encodedResourcesRoot;
    private final CachingResourceManager encoded;
    private final ContentEncodingRepository contentEncodingRepository;
    private final int minResourceSize;
    private final int maxResourceSize;
    private final Predicate encodingAllowed;

    private final ConcurrentMap<LockKey, Object> fileLocks = new ConcurrentHashMap<>();

    public ContentEncodedResourceManager(Path encodedResourcesRoot, CachingResourceManager encodedResourceManager, ContentEncodingRepository contentEncodingRepository, int minResourceSize, int maxResourceSize, Predicate encodingAllowed) {
        this.encodedResourcesRoot = encodedResourcesRoot;
        this.encoded = encodedResourceManager;
        this.contentEncodingRepository = contentEncodingRepository;
        this.minResourceSize = minResourceSize;
        this.maxResourceSize = maxResourceSize;
        this.encodingAllowed = encodingAllowed;
    }

    /**
     * Gets a pre-encoded resource.
     * <p>
     * TODO: blocking / non-blocking semantics
     *
     * @param resource
     * @param exchange
     * @return
     * @throws IOException
     */
    public ContentEncodedResource getResource(final Resource resource, final HttpServerExchange exchange) throws IOException {
        final String path = resource.getPath();
        Path file = resource.getFilePath();
        if (file == null) {
            return null;
        }
        if (minResourceSize > 0 && resource.getContentLength() < minResourceSize ||
                maxResourceSize > 0 && resource.getContentLength() > maxResourceSize ||
                !(encodingAllowed == null || encodingAllowed.resolve(exchange))) {
            return null;
        }
        AllowedContentEncodings encodings = contentEncodingRepository.getContentEncodings(exchange);
        if (encodings == null || encodings.isNoEncodingsAllowed()) {
            return null;
        }
        EncodingMapping encoding = encodings.getEncoding();
        if (encoding == null || encoding.getName().equals(ContentEncodingRepository.IDENTITY)) {
            return null;
        }
        String newPath = path + ".undertow.encoding." + encoding.getName();
        Resource preCompressed = encoded.getResource(newPath);
        if (preCompressed != null) {
            return new ContentEncodedResource(preCompressed, encoding.getName());
        }
        final LockKey key = new LockKey(path, encoding.getName());
        if (fileLocks.putIfAbsent(key, this) != null) {
            //another thread is already compressing
            //we don't do anything fancy here, just return and serve non-compressed content
            return null;
        }
        FileChannel targetFileChannel = null;
        FileChannel sourceFileChannel = null;
        try {
            //double check, the compressing thread could have finished just before we acquired the lock
            preCompressed = encoded.getResource(newPath);
            if (preCompressed != null) {
                return new ContentEncodedResource(preCompressed, encoding.getName());
            }

            final Path finalTarget = encodedResourcesRoot.resolve(newPath);
            final Path tempTarget = encodedResourcesRoot.resolve(newPath);

            //horrible hack to work around XNIO issue
            OutputStream tmp = Files.newOutputStream(tempTarget);
            try {
                tmp.close();
            } finally {
                IoUtils.safeClose(tmp);
            }

            targetFileChannel = FileChannel.open(tempTarget, StandardOpenOption.READ, StandardOpenOption.WRITE);
            sourceFileChannel = FileChannel.open(file, StandardOpenOption.READ);

            StreamSinkConduit conduit = encoding.getEncoding().getResponseWrapper().wrap(new ImmediateConduitFactory<StreamSinkConduit>(new FileConduitTarget(targetFileChannel, exchange)), exchange);
            final ConduitStreamSinkChannel targetChannel = new ConduitStreamSinkChannel(null, conduit);
            long transferred = sourceFileChannel.transferTo(0, resource.getContentLength(), targetChannel);
            targetChannel.shutdownWrites();
            org.xnio.channels.Channels.flushBlocking(targetChannel);
            if (transferred != resource.getContentLength()) {
                UndertowLogger.REQUEST_LOGGER.failedToWritePreCachedFile();
            }
            Files.move(tempTarget, finalTarget);
            encoded.invalidate(newPath);
            final Resource encodedResource = encoded.getResource(newPath);
            return new ContentEncodedResource(encodedResource, encoding.getName());
        } finally {
            IoUtils.safeClose(targetFileChannel);
            IoUtils.safeClose(sourceFileChannel);
            fileLocks.remove(key);
        }
    }

    private static final class LockKey {
        private final String path;
        private final String encoding;

        private LockKey(String path, String encoding) {
            this.path = path;
            this.encoding = encoding;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LockKey lockKey = (LockKey) o;

            if (encoding != null ? !encoding.equals(lockKey.encoding) : lockKey.encoding != null) return false;
            if (path != null ? !path.equals(lockKey.path) : lockKey.path != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = path != null ? path.hashCode() : 0;
            result = 31 * result + (encoding != null ? encoding.hashCode() : 0);
            return result;
        }
    }

    private static final class FileConduitTarget implements StreamSinkConduit {
        private final FileChannel fileChannel;
        private final HttpServerExchange exchange;
        private WriteReadyHandler writeReadyHandler;
        private boolean writesResumed = false;

        private FileConduitTarget(FileChannel fileChannel, HttpServerExchange exchange) {
            this.fileChannel = fileChannel;
            this.exchange = exchange;
        }

        @Override
        public long transferFrom(FileChannel fileChannel, long l, long l2) throws IOException {
            return this.fileChannel.transferFrom(fileChannel, l, l2);
        }

        @Override
        public long transferFrom(StreamSourceChannel streamSourceChannel, long l, ByteBuffer byteBuffer) throws IOException {
            return IoUtils.transfer(streamSourceChannel, l, byteBuffer, fileChannel);
        }

        @Override
        public int write(ByteBuffer byteBuffer) throws IOException {
            return fileChannel.write(byteBuffer);
        }

        @Override
        public long write(ByteBuffer[] byteBuffers, int i, int i2) throws IOException {
            return fileChannel.write(byteBuffers, i, i2);
        }

        @Override
        public int writeFinal(ByteBuffer src) throws IOException {
            return Conduits.writeFinalBasic(this, src);
        }

        @Override
        public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
            return Conduits.writeFinalBasic(this, srcs, offset, length);
        }

        @Override
        public void terminateWrites() throws IOException {
            fileChannel.close();
        }

        @Override
        public boolean isWriteShutdown() {
            return !fileChannel.isOpen();
        }

        @Override
        public void resumeWrites() {
            wakeupWrites();
        }

        @Override
        public void suspendWrites() {
            writesResumed = false;
        }

        @Override
        public void wakeupWrites() {
            if (writeReadyHandler != null) {
                writesResumed = true;
                while (writesResumed && writeReadyHandler != null) {
                    writeReadyHandler.writeReady();
                }
            }
        }

        @Override
        public boolean isWriteResumed() {
            return writesResumed;
        }

        @Override
        public void awaitWritable() throws IOException {
        }

        @Override
        public void awaitWritable(long l, TimeUnit timeUnit) throws IOException {
        }

        @Override
        public XnioIoThread getWriteThread() {
            return exchange.getIoThread();
        }

        @Override
        public void setWriteReadyHandler(WriteReadyHandler writeReadyHandler) {
            this.writeReadyHandler = writeReadyHandler;
        }

        @Override
        public void truncateWrites() throws IOException {
            fileChannel.close();
        }

        @Override
        public boolean flush() throws IOException {
            return true;
        }

        @Override
        public XnioWorker getWorker() {
            return exchange.getConnection().getWorker();
        }
    }
}
