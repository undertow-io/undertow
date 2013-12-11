package io.undertow.server.protocol.http;

import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        if (exchange.getConnection() instanceof HttpServerConnection) {
            if (((HttpServerConnection) exchange.getConnection()).getExtraBytes() != null) {
                //we have already received some of the request body
                //so according to the RFC we do not need to send the Continue
                return false;
            }
        }
        List<String> expect = exchange.getRequestHeaders().get(Headers.EXPECT);
        if (expect != null) {
            for (String header : expect) {
                if (header.equalsIgnoreCase(CONTINUE)) {
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
        internalSendContinueResponse(exchange, callback);
    }

    /**
     * Creates a response sender that can be used to send a HTTP 100-continue response.
     *
     * @param exchange The exchange
     * @return The response sender
     */
    public static ContinueResponseSender createResponseSender(final HttpServerExchange exchange) {
        if (!exchange.isResponseChannelAvailable()) {
            throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
        }

        HeaderValues transfer_encoding = exchange.getRequestHeaders().get(Headers.TRANSFER_ENCODING);
        exchange.getRequestHeaders().remove(Headers.TRANSFER_ENCODING);
        HttpServerExchange newExchange = exchange.getConnection().sendOutOfBandResponse(exchange);
        if(transfer_encoding != null)
            exchange.getRequestHeaders().putAll(Headers.TRANSFER_ENCODING, transfer_encoding);
        newExchange.setResponseCode(100);
        newExchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
        final StreamSinkChannel responseChannel = newExchange.getResponseChannel();
        return new ContinueResponseSender() {
            boolean shutdown = false;

            @Override
            public boolean send() throws IOException {
                if (!shutdown) {
                    shutdown = true;
                    responseChannel.shutdownWrites();
                }
                return responseChannel.flush();
            }

            @Override
            public void awaitWritable() throws IOException {
                responseChannel.awaitWritable();
            }

            @Override
            public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
                responseChannel.awaitWritable(time, timeUnit);
            }
        };
    }

    /**
     * Sends a continue response using blocking IO
     *
     * @param exchange The exchange
     */
    public static void sendContinueResponseBlocking(final HttpServerExchange exchange) throws IOException {
        if (!exchange.isResponseChannelAvailable()) {
            throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
        }
        HeaderValues transfer_encoding = exchange.getRequestHeaders().get(Headers.TRANSFER_ENCODING);
        exchange.getRequestHeaders().remove(Headers.TRANSFER_ENCODING);
        HttpServerExchange newExchange = exchange.getConnection().sendOutOfBandResponse(exchange);
        if(transfer_encoding != null)
            exchange.getRequestHeaders().putAll(Headers.TRANSFER_ENCODING, transfer_encoding);
        newExchange.setResponseCode(100);
        newExchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
        newExchange.startBlocking();
        newExchange.getOutputStream().close();
        newExchange.getInputStream().close();
    }

    /**
     * Sets a 417 response code and ends the exchange.
     *
     * @param exchange The exchange to reject
     */
    public static void rejectExchange(final HttpServerExchange exchange) {
        exchange.setResponseCode(417);
        exchange.setPersistent(false);
        exchange.endExchange();
    }


    private static void internalSendContinueResponse(final HttpServerExchange exchange, final IoCallback callback) {
        HeaderValues transfer_encoding = exchange.getRequestHeaders().get(Headers.TRANSFER_ENCODING);
        exchange.getRequestHeaders().remove(Headers.TRANSFER_ENCODING);
        HttpServerExchange newExchange = exchange.getConnection().sendOutOfBandResponse(exchange);
        if(transfer_encoding != null)
            exchange.getRequestHeaders().putAll(Headers.TRANSFER_ENCODING, transfer_encoding);
        newExchange.setResponseCode(100);
        newExchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
        final StreamSinkChannel responseChannel = newExchange.getResponseChannel();
        try {
            responseChannel.shutdownWrites();
            if (!responseChannel.flush()) {
                exchange.dispatch();
                responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                        new ChannelListener<StreamSinkChannel>() {
                            @Override
                            public void handleEvent(StreamSinkChannel channel) {
                                callback.onComplete(exchange, null);
                                channel.suspendWrites();
                            }
                        }, new ChannelExceptionHandler<Channel>() {
                            @Override
                            public void handleException(Channel channel, IOException e) {
                                callback.onException(exchange, null, e);
                            }
                        }
                ));
                responseChannel.resumeWrites();
            } else {
                callback.onComplete(exchange, null);
            }
        } catch (IOException e) {
            callback.onException(exchange, null, e);
        }
    }

    /**
     * A continue response that is in the process of being sent.
     */
    public interface ContinueResponseSender {

        /**
         * Continue sending the response.
         *
         * @return true if the response is fully sent, false otherwise.
         */
        boolean send() throws IOException;

        void awaitWritable() throws IOException;

        void awaitWritable(long time, final TimeUnit timeUnit) throws IOException;

    }

}
