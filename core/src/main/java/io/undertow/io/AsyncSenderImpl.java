package io.undertow.io;

import java.io.IOException;
import java.nio.ByteBuffer;
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

    private StreamSinkChannel channel;
    private final HttpServerExchange exchange;
    private ByteBuffer[] buffer;
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
                callback.onException(exchange, AsyncSenderImpl.this, e);
            }
        }
    };


    public AsyncSenderImpl(final HttpServerExchange exchange) {
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
            callback.onException(exchange, this, e);
        }
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
        send(ByteBuffer.wrap(data.getBytes(utf8)), callback);
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        send(ByteBuffer.wrap(data.getBytes(charset)), callback);
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
                                callback.onComplete(exchange, AsyncSenderImpl.this);
                            }
                        }, new ChannelExceptionHandler<StreamSinkChannel>() {
                            @Override
                            public void handleException(final StreamSinkChannel channel, final IOException exception) {
                                callback.onException(exchange, AsyncSenderImpl.this, exception);
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
                    callback.onException(exchange, this, e);
                }
            } else {
                return;
            }

        }
    }
}
