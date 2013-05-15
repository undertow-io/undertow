package io.undertow.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.charset.Charset;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import org.xnio.Buffers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author Stuart Douglas
 */
public class AsyncSenderImpl implements Sender {

    private static final Charset utf8 = Charset.forName("UTF-8");

    private final StreamSinkChannel streamSinkChannel;
    private final HttpServerExchange exchange;
    private ByteBuffer[] buffer;
    private IoCallback callback;
    private boolean inCallback;

    private final ChannelListener<Channel> writeListener = new ChannelListener<Channel>() {
        @Override
        public void handleEvent(final Channel channel) {
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
                callback.onException(exchange, AsyncSenderImpl.this, e);
            }
        }
    };


    public AsyncSenderImpl(final StreamSinkChannel streamSinkChannel, final HttpServerExchange exchange) {
        this.streamSinkChannel = streamSinkChannel;
        this.exchange = exchange;
    }


    @Override
    public void send(final ByteBuffer buffer, final IoCallback callback) {
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if (this.buffer != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
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
                int res = streamSinkChannel.write(buffer);
                if (res == 0) {
                    this.buffer = new ByteBuffer[]{buffer};
                    this.callback = callback;
                    streamSinkChannel.getWriteSetter().set(writeListener);
                    streamSinkChannel.resumeWrites();
                    return;
                }
            } while (buffer.hasRemaining());
            invokeOnComplete();

        } catch (IOException e) {
            callback.onException(exchange, this, e);
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

        long t = Buffers.remaining(buffer);
        final long total = t;
        long written = 0;

        try {
            do {
                long res = streamSinkChannel.write(buffer);
                written += res;
                if (res == 0) {
                    this.buffer = buffer;
                    this.callback = callback;
                    streamSinkChannel.getWriteSetter().set(writeListener);
                    streamSinkChannel.resumeWrites();
                    return;
                }
            } while (written < total);
            invokeOnComplete();

        } catch (IOException e) {
            callback.onException(exchange, this, e);
        }
    }

    @Override
    public void send(final String data, final IoCallback callback) {
        send(ByteBuffer.wrap(data.getBytes(utf8)), callback);
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        send(ByteBuffer.wrap(data.getBytes(charset)), callback);
    }

    @Override
    public void close(final IoCallback callback) {
        try {
            streamSinkChannel.shutdownWrites();
            if (!streamSinkChannel.flush()) {
                streamSinkChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                        new ChannelListener<StreamSinkChannel>() {
                            @Override
                            public void handleEvent(final StreamSinkChannel channel) {
                                callback.onComplete(exchange, AsyncSenderImpl.this);
                            }
                        }, new ChannelExceptionHandler<StreamSinkChannel>() {
                            @Override
                            public void handleException(final StreamSinkChannel channel, final IOException exception) {
                                callback.onException(exchange, AsyncSenderImpl.this, exception);
                            }
                        }
                ));
                streamSinkChannel.resumeWrites();
            } else {
                if (callback != null) {
                    callback.onComplete(exchange, this);
                }
            }
        } catch (IOException e) {
            if (callback != null) {
                callback.onException(exchange, this, e);
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
            IoCallback callback = this.callback;
            this.buffer = null;
            this.callback = null;
            inCallback = true;
            try {
                callback.onComplete(exchange, this);
            } finally {
                inCallback = false;
            }

            if (this.buffer != null) {
                long t = Buffers.remaining(buffer);
                final long total = t;
                long written = 0;

                try {
                    do {
                        long res = streamSinkChannel.write(buffer);
                        written += res;
                        if (res == 0) {
                            streamSinkChannel.getWriteSetter().set(writeListener);
                            streamSinkChannel.resumeWrites();
                            return;
                        }
                    } while (written < total);
                    //we loop and invoke onComplete again
                } catch (IOException e) {
                    callback.onException(exchange, this, e);
                }
            } else {
                return;
            }

        }
    }
}
