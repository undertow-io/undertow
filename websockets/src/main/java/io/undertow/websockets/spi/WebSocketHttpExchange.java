package io.undertow.websockets.spi;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.xnio.Pool;


/**
 * An abstraction for a Http exchange. Undertow uses 3 different types of exchanges:
 * <p/>
 * - async
 * - blocking
 * - servlet
 * <p/>
 * This class provides a way to operate on the underling exchange while providing the
 * correct semantics regardless of the underlying exchange type.
 * <p/>
 * The main use case for this is web sockets. Web sockets should be able to perform
 * a handshake regardless of the nature of the underlying request, while still respecting
 * servlet filters, security etc.
 *
 * @author Stuart Douglas
 */
public interface WebSocketHttpExchange extends Closeable {

    /**
     * gets the first request header with the specified name
     *
     * @param headerName The header name
     * @return The header value, or null
     */
    String getRequestHeader(final String headerName);

    /**
     *
     * @return An unmodifiable map of request headers
     */
    Map<String, List<String>> getRequestHeaders();

    /**
     * get a response header
     *
     * @param headerName The header name
     * @return The header value, or null
     */
    String getResponseHeader(final String headerName);

    /**
     *
     * @return An unmodifiable map of response headers
     */
    Map<String, List<String>> getResponseHeaders();


    /**
     * Sets the response headers
     */
    void setResponseHeaders(final Map<String, List<String>> headers);

    /**
     * Set a response header
     *
     * @param headerName  The header name
     * @param headerValue The header value
     */
    void setResponseHeader(final String headerName, final String headerValue);

    /**
     * Set a http response code
     *
     * @param code
     */
    void setResponesCode(int code);

    /**
     * Upgrade the underlying channel
     *
     * @param upgradeCallback
     */
    void upgradeChannel(final UpgradeCallback upgradeCallback);

    /**
     * Send some data, ending the exchange on completion.
     *
     * Depending on the nature of the exchange the data may be written out with either
     * blocking or async IO, and the exchange may end immediately or once the call stack returns.
     *
     * Either way, the exchange should not be modified after this method is invoked
     *
     * @param data The data
     * @param callback The callback
     */
    void sendData(final ByteBuffer data, final WriteCallback callback);

    /**
     * Gets the body of the request.
     *
     * @param callback The callback that is invoked when the body is fully read
     */
    void readRequestData(final ReadCallback callback);

    /**
     * End the exchange normally. If this is a blocking exchange this may be a noop, and the exchange
     * will actually end when the call stack returns
     */
    void endExchange();

    /**
     * Forcibly close the exchange.
     */
    void close();

    /**
     * Get the request scheme, usually http or https
     *
     * @return The request scheme
     */
    String getRequestScheme();

    /**
     * @return The request URI
     */
    String getRequestURI();

    Pool<ByteBuffer> getBufferPool();

    /**
     *
     * @return The query string
     */
    String getQueryString();

    interface ReadCallback {

        void onRead(final WebSocketHttpExchange exchange, final byte[] data);

        void error(final WebSocketHttpExchange exchange, IOException exception);
    }

    interface WriteCallback {

        void onWrite(final WebSocketHttpExchange exchange);

        void error(final WebSocketHttpExchange exchange, IOException exception);
    }
}
