package io.undertow.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.charset.Charset;

import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author Stuart Douglas
 */
class SenderImpl implements Sender {

    private static final Charset utf8 = Charset.forName("UTF-8");

    private final StreamSinkChannel streamSinkChannel;
    private final HttpServerExchange exchange;

    SenderImpl(final StreamSinkChannel streamSinkChannel, final HttpServerExchange exchange) {
        this.streamSinkChannel = streamSinkChannel;
        this.exchange = exchange;
    }


    @Override
    public void send(final ByteBuffer buffer, final IoCallback callback) {
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        try {
            do {
                int res = streamSinkChannel.write(buffer);
                if (res == 0) {
                    streamSinkChannel.getWriteSetter().set(new ChannelListener<Channel>() {
                        @Override
                        public void handleEvent(final Channel channel) {
                            try {
                                do {
                                    int res = streamSinkChannel.write(buffer);
                                    if (res == 0) {
                                        return;
                                    }
                                } while (buffer.hasRemaining());
                                streamSinkChannel.suspendWrites();
                                callback.onComplete(exchange, SenderImpl.this);
                            } catch (IOException e) {
                                streamSinkChannel.suspendWrites();
                                callback.onException(exchange, SenderImpl.this, e);
                            }
                        }
                    });
                    streamSinkChannel.resumeWrites();
                    return;
                }
            } while (buffer.hasRemaining());
            callback.onComplete(exchange, this);

        } catch (IOException e) {
            callback.onException(exchange, this, e);
        }
    }

    @Override
    public void send(final ByteBuffer[] buffer, final IoCallback callback) {
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }

        long t = 0;
        for (ByteBuffer l : buffer) {
            t += l.remaining();
        }
        final long total = t;
        long written = 0;

        try {
            do {
                long res = streamSinkChannel.write(buffer);
                written += res;
                if (res == 0) {
                    final long finalWritten = written;
                    streamSinkChannel.getWriteSetter().set(new ChannelListener<Channel>() {

                        long written = finalWritten;

                        @Override
                        public void handleEvent(final Channel channel) {
                            try {
                                do {
                                    long res = streamSinkChannel.write(buffer);
                                    written += res;
                                    if (res == 0) {
                                        return;
                                    }
                                } while (written < total);
                                callback.onComplete(exchange, SenderImpl.this);
                            } catch (IOException e) {
                                callback.onException(exchange, SenderImpl.this, e);
                            }
                        }
                    });
                    streamSinkChannel.resumeWrites();
                    return;
                }
            } while (written < total);
            callback.onComplete(exchange, this);

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
                                callback.onComplete(exchange, SenderImpl.this);
                            }
                        }, new ChannelExceptionHandler<StreamSinkChannel>() {
                            @Override
                            public void handleException(final StreamSinkChannel channel, final IOException exception) {
                                callback.onException(exchange, SenderImpl.this, exception);
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
}
