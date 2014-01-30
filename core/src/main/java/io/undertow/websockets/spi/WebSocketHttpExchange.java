package io.undertow.websockets.spi;

import io.undertow.server.HttpUpgradeListener;
import io.undertow.util.AttachmentKey;
import org.xnio.IoFuture;
import org.xnio.Pool;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.List;
import java.util.Map;


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

    <T> void putAttachment(final AttachmentKey<T> key, T value);

    <T> T getAttachment(final AttachmentKey<T> key);

    /**
     * gets the first request header with the specified name
     *
     * @param headerName The header name
     * @return The header value, or null
     */
    String getRequestHeader(final String headerName);

    /**
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
     * Upgrade the underlying channel
     *
     * @param upgradeCallback
     */
    void upgradeChannel(final HttpUpgradeListener upgradeCallback);

    /**
     * Send some data
     *
     * @param data The data
     */
    IoFuture<Void> sendData(final ByteBuffer data);

    /**
     * Gets the body of the request.
     */
    IoFuture<byte[]> readRequestData();

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
     * @return The request URI, including the query string
     */
    String getRequestURI();

    /**
     * @return The buffer pool
     */
    Pool<ByteBuffer> getBufferPool();

    /**
     * @return The query string
     */
    String getQueryString();

    /**
     * Gets the session, if any
     *
     * @return The session object, or null
     */
    Object getSession();

    Map<String,List<String>> getRequestParameters();

    Principal getUserPrincipal();

    boolean isUserInRole(String role);
}
