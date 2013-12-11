package io.undertow.servlet.util;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.util.HttpString;
import io.undertow.util.ImmediatePooled;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Saved servlet request.
 *
 * @author Stuart Douglas
 */
public class SavedRequest {

    private static final String SESSION_KEY = SavedRequest.class.getName();

    private final ByteBuffer data;
    private final HttpString method;
    private final String requestUri;

    public SavedRequest(ByteBuffer data, HttpString method, String requestUri) {
        this.data = data;
        this.method = method;
        this.requestUri = requestUri;
    }

    public static void trySaveRequest(final HttpServerExchange exchange) {
        int maxSize = exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_BUFFERED_REQUEST_SIZE, 16384);
        if (maxSize > 0) {
            //if this request has a body try and cache the response
            if (!exchange.isRequestComplete()) {
                final long requestContentLength = exchange.getRequestContentLength();
                if (requestContentLength > maxSize) {
                    UndertowLogger.REQUEST_LOGGER.debugf("Request to %s was to large to save", exchange.getRequestURI());
                    return;//failed to save the request, we just return
                }
                //TODO: we should really be used pooled buffers
                //TODO: we should probably limit the number of saved requests at any given time
                byte[] buffer = new byte[maxSize];
                int read = 0;
                int res = 0;
                InputStream in = exchange.getInputStream();
                try {
                    while ((res = in.read(buffer)) > 0) {
                        read += res;
                        if (read == maxSize) {
                            UndertowLogger.REQUEST_LOGGER.debugf("Request to %s was to large to save", exchange.getRequestURI());
                            return;//failed to save the request, we just return
                        }
                    }
                    ByteBuffer data = ByteBuffer.wrap(buffer, 0, read);
                    SavedRequest request = new SavedRequest(data, exchange.getRequestMethod(), exchange.getRequestURI());
                    final ServletRequestContext sc = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
                    HttpSessionImpl session = sc.getCurrentServetContext().getSession(exchange, true);
                    session.setAttribute(SESSION_KEY, request);
                } catch (IOException e) {
                    UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                }
            }
        }
    }

    public static void tryRestoreRequest(final HttpServerExchange exchange, HttpSession session) {
        SavedRequest request = (SavedRequest)session.getAttribute(SESSION_KEY);
        if(request != null) {
            if(request.requestUri.equals(exchange.getRequestURI())) {
                UndertowLogger.REQUEST_LOGGER.debugf("restoring request body for request to %s", request.requestUri);
                exchange.setRequestMethod(request.method);
                Connectors.ungetRequestBytes(exchange, new ImmediatePooled<ByteBuffer>(request.data));
                session.removeAttribute(SESSION_KEY);
            }
        }
    }
}
