package io.undertow.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import org.xnio.Buffers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author Stuart Douglas
 */
public class AsyncSenderImpl implements Sender {

    private static final Charset utf8 = Charset.forName("UTF-8");

    private StreamSinkChannel channel;
    private final HttpServerExchange exchange;
    private ByteBuffer[] buffer;
    private Pooled[] pooledBuffers = null;
    private FileChannel fileChannel;
    private IoCallback callback;
    private boolean inCallback;

    private final ChannelListener<StreamSinkChannel> writeListener = new ChannelListener<StreamSinkChannel>() {
        @Override
        public void handleEvent(final StreamSinkChannel streamSinkChannel) {
            try {
                long toWrite = Buffers.remaining(buffer);
                long written = 0;
                while (written < toWrite) {
                    long res = streamSinkChannel.write(buffer, 0, buffer.length);
                    written += res;
                    if (res == 0) {
                        return;
                    }
                }
                streamSinkChannel.suspendWrites();
                invokeOnComplete();
            } catch (IOException e) {
                streamSinkChannel.suspendWrites();
                invokeOnException(callback, e);
            }
        }
    };

    public class TransferTask implements Runnable, ChannelListener<StreamSinkChannel> {
        public boolean run(boolean complete) {
            try {
                FileChannel source = fileChannel;
                long pos = source.position();
                long size = source.size();

                StreamSinkChannel dest = channel;
                if (dest == null) {
                    if (callback == IoCallback.END_EXCHANGE) {
                        if (exchange.getResponseContentLength() == -1) {
                            exchange.setResponseContentLength(size);
                        }
                    }
                    channel = dest = exchange.getResponseChannel();
                    if (dest == null) {
                        throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
                    }
                }

                while (size - pos > 0) {
                    long ret = dest.transferFrom(source, pos, size - pos);
                    pos += ret;
                    if (ret == 0) {
                        source.position(pos);
                        dest.getWriteSetter().set(this);
                        dest.resumeWrites();
                        return false;
                    }
                }

                if (complete) {
                    invokeOnComplete();
                }
            } catch (IOException e) {
                invokeOnException(callback, e);
            }

            return true;
        }

        @Override
        public void handleEvent(StreamSinkChannel channel) {
            channel.suspendWrites();
            channel.getWriteSetter().set(null);
            exchange.dispatch(this);
        }

        @Override
        public void run() {
            run(true);
        }
    }

    private final TransferTask transferTask = new TransferTask();


    public AsyncSenderImpl(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }


    @Override
    public void send(final ByteBuffer buffer, final IoCallback callback) {
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if (this.buffer != null || this.fileChannel != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        StreamSinkChannel channel = this.channel;
        if (channel == null) {
            if (callback == IoCallback.END_EXCHANGE) {
                if (exchange.getResponseContentLength() == -1) {
                    exchange.setResponseContentLength(buffer.remaining());
                }
            }
            this.channel = channel = exchange.getResponseChannel();
            if (channel == null) {
                throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
            }
        }
        this.callback = callback;
        if (inCallback) {
            this.buffer = new ByteBuffer[]{buffer};
            return;
        }
        try {
            do {
                if (buffer.remaining() == 0) {
                    callback.onComplete(exchange, this);
                    return;
                }
                int res = channel.write(buffer);
                if (res == 0) {
                    this.buffer = new ByteBuffer[]{buffer};
                    this.callback = callback;
                    channel.getWriteSetter().set(writeListener);
                    channel.resumeWrites();
                    return;
                }
            } while (buffer.hasRemaining());
            invokeOnComplete();

        } catch (IOException e) {

            invokeOnException(callback, e);
        }
    }

    @Override
    public void send(final ByteBuffer[] buffer, final IoCallback callback) {
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if (this.buffer != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        this.callback = callback;
        if (inCallback) {
            this.buffer = buffer;
            return;
        }

        long totalToWrite = Buffers.remaining(buffer);

        StreamSinkChannel channel = this.channel;
        if (channel == null) {
            if (callback == IoCallback.END_EXCHANGE) {
                if (exchange.getResponseContentLength() == -1) {
                    exchange.setResponseContentLength(totalToWrite);
                }
            }
            this.channel = channel = exchange.getResponseChannel();
            if (channel == null) {
                throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
            }
        }

        final long total = totalToWrite;
        long written = 0;

        try {
            do {
                long res = channel.write(buffer);
                written += res;
                if (res == 0) {
                    this.buffer = buffer;
                    this.callback = callback;
                    channel.getWriteSetter().set(writeListener);
                    channel.resumeWrites();
                    return;
                }
            } while (written < total);
            invokeOnComplete();

        } catch (IOException e) {
            invokeOnException(callback, e);
        }
    }


    @Override
    public void transferFrom(FileChannel source, IoCallback callback) {
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if (this.fileChannel != null || this.buffer != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }

        this.callback = callback;
        this.fileChannel = source;
        if (inCallback) {
            return;
        }

        if (exchange.isInIoThread()) {
            exchange.dispatch(transferTask);
            return;
        }

        transferTask.run();
    }

    @Override
    public void send(final ByteBuffer buffer) {
        send(buffer, IoCallback.END_EXCHANGE);
    }

    @Override
    public void send(final ByteBuffer[] buffer) {
        send(buffer, IoCallback.END_EXCHANGE);
    }

    @Override
    public void send(final String data, final IoCallback callback) {
        send(data, utf8, callback);
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        ByteBuffer bytes = ByteBuffer.wrap(data.getBytes(charset));
        int i = 0;
        ByteBuffer[] bufs = null;
        while (bytes.hasRemaining()) {
            Pooled<ByteBuffer> pooled = exchange.getConnection().getBufferPool().allocate();
            if (bufs == null) {
                int noBufs = (bytes.remaining() + pooled.getResource().remaining() - 1) / pooled.getResource().remaining(); //round up division trick
                pooledBuffers = new Pooled[noBufs];
                bufs = new ByteBuffer[noBufs];
            }
            pooledBuffers[i] = pooled;
            bufs[i] = pooled.getResource();
            Buffers.copy(pooled.getResource(), bytes);
            pooled.getResource().flip();
            ++i;
        }
        send(bufs, callback);
    }

    @Override
    public void send(final String data) {
        send(data, IoCallback.END_EXCHANGE);
    }

    @Override
    public void send(final String data, final Charset charset) {
        send(data, charset, IoCallback.END_EXCHANGE);
    }

    @Override
    public void close(final IoCallback callback) {
        try {
            StreamSinkChannel channel = this.channel;
            if (channel == null) {
                if (exchange.getResponseContentLength() == -1) {
                    exchange.setResponseContentLength(0);
                }
                this.channel = channel = exchange.getResponseChannel();
                if (channel == null) {
                    throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
                }
            }
            channel.shutdownWrites();
            if (!channel.flush()) {
                channel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                        new ChannelListener<StreamSinkChannel>() {
                            @Override
                            public void handleEvent(final StreamSinkChannel channel) {
                                if(callback != null) {
                                    callback.onComplete(exchange, AsyncSenderImpl.this);
                                }
                            }
                        }, new ChannelExceptionHandler<StreamSinkChannel>() {
                            @Override
                            public void handleException(final StreamSinkChannel channel, final IOException exception) {
                                try {
                                    if(callback != null) {
                                        invokeOnException(callback, exception);
                                    }
                                } finally {
                                    IoUtils.safeClose(channel);
                                }
                            }
                        }
                ));
                channel.resumeWrites();
            } else {
                if (callback != null) {
                    callback.onComplete(exchange, this);
                }
            }
        } catch (IOException e) {
            if (callback != null) {
                invokeOnException(callback, e);
            }
        }
    }

    @Override
    public void close() {
        close(null);
    }

    /**
     * Invokes the onComplete method. If send is called again in onComplete then
     * we loop and write it out. This prevents possible stack overflows due to recursion
     */
    private void invokeOnComplete() {
        for (; ; ) {
            if (pooledBuffers != null) {
                for (Pooled buffer : pooledBuffers) {
                    buffer.free();
                }
                pooledBuffers = null;
            }
            IoCallback callback = this.callback;
            this.buffer = null;
            this.fileChannel = null;
            this.callback = null;
            inCallback = true;
            try {
                callback.onComplete(exchange, this);
            } finally {
                inCallback = false;
            }

            StreamSinkChannel channel = this.channel;
            if (this.buffer != null) {
                long t = Buffers.remaining(buffer);
                final long total = t;
                long written = 0;

                try {
                    do {
                        long res = channel.write(buffer);
                        written += res;
                        if (res == 0) {
                            channel.getWriteSetter().set(writeListener);
                            channel.resumeWrites();
                            return;
                        }
                    } while (written < total);
                    //we loop and invoke onComplete again
                } catch (IOException e) {
                    invokeOnException(callback, e);
                }
            } else if (this.fileChannel != null) {
                if (!transferTask.run(false)) {
                    return;
                }
            } else {
                return;
            }

        }
    }


    private void invokeOnException(IoCallback callback, IOException e) {

        if (pooledBuffers != null) {
            for (Pooled buffer : pooledBuffers) {
                buffer.free();
            }
            pooledBuffers = null;
        }
        callback.onException(exchange, this, e);
    }
}
