package io.undertow.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import io.undertow.UndertowMessages;
import io.undertow.conduits.PipelingBufferingStreamSinkConduit;
import io.undertow.io.IoCallback;
import io.undertow.util.Headers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;

/**
 * Class that provides support for dealing with HTTP 100 (Continue) responses.
 * <p/>
 * Note that if a client is pipelining some requests and sending continue for others this
 * could cause problems if the pipelining buffer is enabled.
 *
 * @author Stuart Douglas
 */
public class HttpContinue {

    public static final String CONTINUE = "100-continue";

    private static final ByteBuffer BUFFER = ByteBuffer.wrap("HTTP/1.1 100 Continue\r\nConnection: keep-alive\r\n\r\n".getBytes());

    /**
     * Returns true if this exchange requires the server to send a 100 (Continue) response.
     *
     * @param exchange The exchange
     * @return <code>true</code> if the server needs to send a continue response
     */
    public static boolean requiresContinueResponse(final HttpServerExchange exchange) {
        if (!exchange.isHttp11()) {
            return false;
        }
        if (exchange.getConnection().getExtraBytes() != null) {
            //we have already received some of the request body
            //so according to the RFC we do not need to send the Continue
            return false;
        }
        List<String> expect = exchange.getRequestHeaders().get(Headers.EXPECT);
        if (expect != null) {
            for (String header : expect) {
                if (header.toLowerCase().equals(CONTINUE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sends a continuation using async IO, and calls back when it is complete.
     *
     * @param exchange The exchange
     * @param callback The completion callback
     */
    public static void sendContinueResponse(final HttpServerExchange exchange, final IoCallback callback) {
        if (!exchange.isResponseChannelAvailable()) {
            throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
        }
        final PipelingBufferingStreamSinkConduit pipelingbuffer = exchange.getAttachment(PipelingBufferingStreamSinkConduit.ATTACHMENT_KEY);
        final StreamConnection channel = exchange.getConnection().getChannel();
        final ConduitStreamSinkChannel sinkChannel = channel.getSinkChannel();
        if (pipelingbuffer != null) {
            try {
                if (!pipelingbuffer.flushPipelinedData()) {
                    sinkChannel.setWriteListener(new ChannelListener<StreamSinkChannel>() {
                        @Override
                        public void handleEvent(final StreamSinkChannel channel) {
                            try {
                                if (pipelingbuffer.flushPipelinedData()) {
                                    channel.suspendWrites();
                                    internalSendContinueResponse(exchange, channel, callback);
                                }
                            } catch (IOException e) {
                                callback.onException(exchange, null, e);
                                IoUtils.safeClose(channel);
                                return;
                            }
                        }
                    });
                    sinkChannel.resumeWrites();
                }
            } catch (IOException e) {
                callback.onException(exchange, null, e);
                return;
            }
        }
        internalSendContinueResponse(exchange, sinkChannel, callback);
    }


    /**
     * Sends a continue response using blocking IO
     *
     * @param exchange The exchange
     */
    public static void sendContinueResponse(final HttpServerExchange exchange) throws IOException {
        if (!exchange.isResponseChannelAvailable()) {
            throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
        }
        final PipelingBufferingStreamSinkConduit pipelingBuffer = exchange.getAttachment(PipelingBufferingStreamSinkConduit.ATTACHMENT_KEY);
        final StreamConnection channel = exchange.getConnection().getChannel();
        if (pipelingBuffer != null) {
            if (!pipelingBuffer.flushPipelinedData()) {
                channel.getSinkChannel().awaitWritable();
            }
        }
        final ByteBuffer buf = BUFFER.duplicate();
        channel.getSinkChannel().write(buf);
        while (buf.hasRemaining()) {
            channel.getSinkChannel().awaitWritable();
            channel.getSinkChannel().write(buf);
        }
        while (!channel.getSinkChannel().flush()) {
            channel.getSinkChannel().awaitWritable();
        }
    }

    /**
     * Sets a 417 response code and ends the exchange.
     *
     * @param exchange The exchange to reject
     */
    public static void rejectExchange(final HttpServerExchange exchange) {
        exchange.setResponseCode(417);
        exchange.endExchange();
    }


    private static void internalSendContinueResponse(final HttpServerExchange exchange, final StreamSinkChannel channel, final IoCallback callback) {
        final ByteBuffer buf = BUFFER.duplicate();
        int res = 0;
        do {
            try {
                res = channel.write(buf);
                if (res == 0) {
                    channel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        @Override
                        public void handleEvent(final StreamSinkChannel channel) {
                            int res = 0;
                            do {
                                try {
                                    res = channel.write(buf);
                                    if (res == 0) {
                                        return;
                                    }
                                } catch (IOException e) {
                                    callback.onException(exchange, null, e);
                                    return;
                                }
                            } while (buf.hasRemaining());
                            channel.suspendWrites();
                            flushChannel(exchange, channel, callback);
                        }
                    });
                    channel.resumeWrites();
                }
            } catch (IOException e) {
                callback.onException(exchange, null, e);
                return;
            }
        } while (buf.hasRemaining());
        flushChannel(exchange, channel, callback);
    }

    private static void flushChannel(final HttpServerExchange exchange, final StreamSinkChannel channel, final IoCallback callback) {
        try {
            if (!channel.flush()) {
                channel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                        new ChannelListener<StreamSinkChannel>() {
                            @Override
                            public void handleEvent(final StreamSinkChannel channel) {
                                callback.onComplete(exchange, null);
                                channel.suspendWrites();
                            }
                        }, new ChannelExceptionHandler<StreamSinkChannel>() {
                            @Override
                            public void handleException(final StreamSinkChannel channel, final IOException exception) {
                                callback.onException(exchange, null, exception);
                                channel.suspendWrites();
                            }
                        }
                ));
                channel.resumeWrites();
            } else {
                callback.onComplete(exchange, null);
            }
        } catch (IOException e) {
            callback.onException(exchange, null, e);
            return;
        }
    }

}
