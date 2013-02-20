package io.undertow.client;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.conduits.ChunkedStreamSourceConduit;
import io.undertow.conduits.ConduitListener;
import io.undertow.conduits.FixedLengthStreamSourceConduit;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import org.xnio.OptionMap;
import org.xnio.Result;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.EmptyStreamSourceConduit;
import org.xnio.conduits.StreamSourceChannelWrappingConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;

/**
 * A pending http request.
 *
 * @author Emanuel Muckenhuber
 */
public final class PendingHttpRequest {

    private final ResponseParseState parseState = new ResponseParseState();

    private int statusCode;
    private HttpString protocol;
    private String reasonPhrase;
    private final HeaderMap responseHeaders = new HeaderMap();

    private final boolean pipeline;
    private final boolean keepAlive;
    private final HttpClientRequestImpl request;
    private final HttpClientConnectionImpl connection;
    private final Result<HttpClientResponse> result;

    private volatile int state = INITIAL;
    private static final AtomicIntegerFieldUpdater<PendingHttpRequest> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(PendingHttpRequest.class, "state");

    private static final int INITIAL = 1 << 0;
    private static final int SENDING_REQUEST = 1 << 1;
    private static final int RECEIVING = 1 << 2;
    private static final int COMPLETED = 1 << 3;

    PendingHttpRequest(final HttpClientRequestImpl request, final HttpClientConnectionImpl connection,
                       final boolean keepAlive, final boolean pipeline, final Result<HttpClientResponse> result) {
        this.keepAlive = keepAlive;
        this.request = request;
        this.connection = connection;
        this.pipeline = pipeline;
        this.result = result;
    }

    public ResponseParseState getParseState() {
        return parseState;
    }

    HeaderMap getResponseHeaders() {
        return responseHeaders;
    }

    int getStatusCode() {
        return statusCode;
    }

    void setStatusCode(final int statusCode) {
        this.statusCode = statusCode;
    }

    String getReasonPhrase() {
        return reasonPhrase;
    }

    void setReasonPhrase(final String reasonPhrase) {
        this.reasonPhrase = reasonPhrase;
    }

    HttpString getProtocol() {
        return protocol;
    }

    @SuppressWarnings("unused")
    void setProtocol(final HttpString protocol) {
        this.protocol = protocol;
    }

    HttpClientRequest getRequest() {
        return request;
    }

    protected void setCancelled() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, COMPLETED)) {
                return;
            }
            newVal = oldVal | COMPLETED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        result.setCancelled();
    }

    protected void setFailed(final IOException e) {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, COMPLETED)) {
                return;
            }
            newVal = oldVal | COMPLETED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        result.setException(e);
    }

    /**
     * Start writing the response.
     */
    protected void sendRequest() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (anyAreSet(oldVal, SENDING_REQUEST | COMPLETED)) {
                return;
            }
            newVal = oldVal | SENDING_REQUEST;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        // Start the response
        request.startSending(this);
        // For empty streams the callbacks are getting called before
        if(allAreSet(newVal, SENDING_REQUEST | RECEIVING)) {
            // Prevent subsequent requests if the connection should be closed
            boolean shutdownWrites = ! keepAlive;
            if(shutdownWrites) {
                try {
                    connection.requestConnectionClose();
                } catch (IOException e) {
                    UndertowLogger.CLIENT_LOGGER.debugf(e, "failed to shutdown writes");
                }
            }
            // Done sending
            connection.sendingCompleted(this);
        }
    }

    /**
     * Notification after the request was sent.
     */
    protected void requestSent() {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (anyAreSet(oldVal, RECEIVING | COMPLETED)) {
                return;
            }
            newVal = oldVal | RECEIVING;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        // If the gate is open and we are done writing
        if(allAreSet(newVal, SENDING_REQUEST | RECEIVING)) {
            boolean shutdownWrites = ! keepAlive;
            if(shutdownWrites) {
                try {
                    connection.requestConnectionClose();
                } catch (IOException e) {
                    UndertowLogger.CLIENT_LOGGER.debugf(e, "failed to shutdown writes");
                }
            }
            // Done sending
            connection.sendingCompleted(this);
        }
    }

    /**
     * Completed when the response was fully read.
     *
     * @param close whether the connection should be closed after this request or not
     */
    protected void completed(boolean close) {
        int oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, COMPLETED)) {
                return;
            }
            newVal = oldVal | COMPLETED;
        } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
        if(close) {
            try {
                connection.requestConnectionClose();
            } catch (IOException e) {
                UndertowLogger.CLIENT_LOGGER.debugf(e, "failed to shutdown reads and writes");
            }
        }
        // Complete
        connection.requestCompleted(this);
    }

    /**
     * Handle a fully parsed response.
     *
     * @param connection the http connection
     * @param channel the response channel
     */
    void handleResponseComplete(final HttpClientConnectionImpl connection, PushBackStreamChannel channel) {
        assert parseState.isComplete();
        UndertowLogger.CLIENT_LOGGER.tracef("reading response headers complete for %s", this);

        final HeaderMap headers = getResponseHeaders();
        final boolean http11 = Protocols.HTTP_1_1.equals(getProtocol());

        boolean closeConnection;
        if(http11) {
            closeConnection = Headers.CLOSE.equals(new HttpString(headers.getFirst(Headers.CONNECTION)));
        } else if (Protocols.HTTP_1_0.equals(getProtocol())) {
            closeConnection = ! Headers.KEEP_ALIVE.equals(new HttpString(headers.getFirst(Headers.CONNECTION)));
        } else {
            closeConnection = true;
        }

        boolean noContent = false;
        final int responseCode = this.statusCode;
        if ((responseCode >= 100 && responseCode < 200)
                || responseCode == 204
                || responseCode == 304) {

            noContent = true;
        }
        if(Methods.HEAD_STRING.equals(request.getMethod())) {
            noContent = true;
        }
        // Process the content length and transfer encodings
        StreamSourceConduit conduit = new StreamSourceChannelWrappingConduit(channel);
        long contentLength = -1;
        if(noContent) {
            conduit = new EmptyStreamSourceConduit(channel.getIoThread());
        } else {
            String transferEncoding = Headers.IDENTITY.toString();
            if (headers.contains(Headers.TRANSFER_ENCODING)) {
                transferEncoding = headers.getLast(Headers.TRANSFER_ENCODING);
            } else if (http11 && ! headers.contains(Headers.CONTENT_LENGTH)) {
                transferEncoding = Headers.CHUNKED.toString();
            }

            if (! transferEncoding.equals(Headers.IDENTITY.toString())) {
                // TODO something without HttpServerExchange
                conduit = new ChunkedStreamSourceConduit(conduit, null, getFinishListener(closeConnection), maxEntitySize(connection.getOptions()));
            } else if (headers.contains(Headers.CONTENT_LENGTH)) {
                contentLength = Long.parseLong(headers.getFirst(Headers.CONTENT_LENGTH));
                if(contentLength == 0L) {
                    conduit = new EmptyStreamSourceConduit(channel.getIoThread());
                    noContent = true;
                } else {
                    conduit = new FixedLengthStreamSourceConduit(conduit, contentLength, getFinishListener(closeConnection));
                }
            } else {
                closeConnection = true;
            }
        }
        // Create the http response
        final StreamSourceChannel responseChannel = new ConduitStreamSourceChannel(channel, conduit);
        final HttpClientResponse response = new HttpClientResponse(this, contentLength, responseChannel);
        result.setResult(response);

        // If there is no content to read, complete the request right away
        if(noContent) {
            completed(closeConnection);
        }
    }

    boolean allowPipeline() {
        return pipeline;
    }

    ConduitListener<StreamSourceConduit> getFinishListener(final boolean closeConnection) {
        return new ConduitListener<StreamSourceConduit>() {
            @Override
            public void handleEvent(StreamSourceConduit conduit) {
                completed(closeConnection);
            }
        };
    }

    private static long maxEntitySize(final OptionMap options) {
        return options.get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
    }

}
