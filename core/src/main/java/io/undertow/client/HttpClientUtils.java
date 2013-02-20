package io.undertow.client;

import io.undertow.UndertowLogger;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.channels.Channel;

/**
 * @author Emanuel Muckenhuber
 */
class HttpClientUtils {

    static <T> void addCallback(final IoFuture<T> result, final HttpClientCallback<T> callback) {
        result.addNotifier(new IoFuture.HandlingNotifier<T, Void>() {
            @Override
            public void handleFailed(IOException exception, Void attachment) {
                callback.failed(exception);
            }

            @Override
            public void handleDone(T data, Void attachment) {
                callback.completed(data);
            }
        }, null);
    }

    static final ChannelListener<StreamSinkChannel> flushingChannelCloseListener() {
        return FLUSHING_CLOSE_LISTENER;
    }

    static final ChannelListener<StreamSinkChannel> FLUSHING_CLOSE_LISTENER = new ChannelListener<StreamSinkChannel>() {
        @Override
        public void handleEvent(StreamSinkChannel channel) {
            try {
                if (!channel.flush()) {
                    channel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                            new ChannelListener<StreamSinkChannel>() {
                                @Override
                                public void handleEvent(final StreamSinkChannel channel) {
                                    channel.suspendWrites();
                                    channel.getWriteSetter().set(null);
                                    IoUtils.safeClose(channel);
                                }
                            }, new ChannelExceptionHandler<Channel>() {
                                @Override
                                public void handleException(final Channel channel, final IOException exception) {
                                    UndertowLogger.CLIENT_LOGGER.debug("Exception ending request", exception);
                                    IoUtils.safeClose(channel);
                                }
                            }
                    ));
                    channel.resumeWrites();
                } else {
                    IoUtils.safeClose(channel);
                }
            } catch(IOException e) {
                UndertowLogger.CLIENT_LOGGER.debug("Exception sending request", e);
                IoUtils.safeClose(channel);
            }
        }

    };

}
