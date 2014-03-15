package io.undertow.server;

import io.undertow.UndertowLogger;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * This class provides the connector part of the {@link HttpServerExchange} API.
 *
 * It contains methods that logically belong on the exchange, however should only be used
 * by connector implementations.
 *
 *
 * @author Stuart Douglas
 */
public class Connectors {


    /**
     * Flattens the exchange cookie map into the response header map. This should be called by a
     * connector just before the response is started.
     *
     * @param exchange The server exchange
     */
    public static void flattenCookies(final HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getResponseCookiesInternal();
        if (cookies != null) {
            for (Map.Entry<String, Cookie> entry : cookies.entrySet()) {
                exchange.getResponseHeaders().add(Headers.SET_COOKIE, getCookieString(entry.getValue()));
            }
        }
    }

    /**
     * Attached buffered data to the exchange. The will generally be used to allow data to be re-read.
     *
     *
     *
     * @param exchange The HTTP server exchange
     * @param buffers The buffers to attach
     */
    public static void ungetRequestBytes(final HttpServerExchange exchange, Pooled<ByteBuffer>... buffers) {
        Pooled<ByteBuffer>[] existing = exchange.getAttachment(HttpServerExchange.BUFFERED_REQUEST_DATA);
        Pooled<ByteBuffer>[] newArray;
        if(existing == null) {
            newArray = new Pooled[buffers.length];
            System.arraycopy(buffers, 0, newArray, 0, buffers.length);
        } else {
            newArray = new Pooled[existing.length + buffers.length];
            System.arraycopy(existing, 0, newArray, 0, existing.length);
            System.arraycopy(buffers, 0, newArray, existing.length, buffers.length);
        }
        exchange.putAttachment(HttpServerExchange.BUFFERED_REQUEST_DATA, newArray); //todo: force some kind of wakeup?
    }

    public static void terminateRequest(final HttpServerExchange exchange) {
        exchange.terminateRequest();
    }

    public static void terminateResponse(final HttpServerExchange exchange) {
        exchange.terminateResponse();
    }

    private static String getCookieString(final Cookie cookie) {
        switch (cookie.getVersion()) {
            case 0:
                return addVersion0ResponseCookieToExchange(cookie);
            case 1:
            default:
                return addVersion1ResponseCookieToExchange(cookie);
        }
    }

    public static void setRequestStartTime(HttpServerExchange exchange) {
        exchange.setRequestStartTime(System.nanoTime());
    }

    private static String addVersion0ResponseCookieToExchange(final Cookie cookie) {
        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        header.append(cookie.getValue());

        if (cookie.getPath() != null) {
            header.append("; path=");
            header.append(cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            header.append("; domain=");
            header.append(cookie.getDomain());
        }
        if (cookie.isSecure()) {
            header.append("; secure");
        }
        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }
        if (cookie.getExpires() != null) {
            header.append("; Expires=");
            header.append(DateUtils.toOldCookieDateString(cookie.getExpires()));
        } else if (cookie.getMaxAge() != null) {
            if (cookie.getMaxAge() >= 0) {
                header.append("; Max-Age=");
                header.append(cookie.getMaxAge());
            }
            if (cookie.getMaxAge() == 0) {
                Date expires = new Date();
                expires.setTime(0);
                header.append("; Expires=");
                header.append(DateUtils.toOldCookieDateString(expires));
            } else if (cookie.getMaxAge() > 0) {
                Date expires = new Date();
                expires.setTime(expires.getTime() + cookie.getMaxAge() * 1000);
                header.append("; Expires=");
                header.append(DateUtils.toOldCookieDateString(expires));
            }
        }
        return header.toString();

    }

    private static String addVersion1ResponseCookieToExchange(final Cookie cookie) {

        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        header.append(cookie.getValue());
        header.append("; Version=1");
        if (cookie.getPath() != null) {
            header.append("; Path=");
            header.append(cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            header.append("; Domain=");
            header.append(cookie.getDomain());
        }
        if (cookie.isDiscard()) {
            header.append("; Discard");
        }
        if (cookie.isSecure()) {
            header.append("; Secure");
        }
        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }
        if (cookie.getMaxAge() != null) {
            if (cookie.getMaxAge() >= 0) {
                header.append("; Max-Age=");
                header.append(cookie.getMaxAge());
            }
        }
        if (cookie.getExpires() != null) {
            header.append("; Expires=");
            header.append(DateUtils.toDateString(cookie.getExpires()));
        }
        return header.toString();
    }

    public static void executeRootHandler(final HttpHandler handler, final HttpServerExchange exchange) {
        try {
            exchange.setInCall(true);
            handler.handleRequest(exchange);
            exchange.setInCall(false);
            boolean resumed = exchange.runResumeReadWrite();
            if (exchange.isDispatched()) {
                if(resumed) {
                    throw new RuntimeException("resumed and dispatched");
                }
                final Runnable dispatchTask = exchange.getDispatchTask();
                Executor executor = exchange.getDispatchExecutor();
                exchange.unDispatch();
                if (dispatchTask != null) {
                    executor = executor == null ? exchange.getConnection().getWorker() : executor;
                    executor.execute(dispatchTask);
                }
            } else if(!resumed) {
                exchange.endExchange();
            }
        } catch (Throwable t) {
            exchange.setInCall(false);
            if (!exchange.isResponseStarted()) {
                exchange.setResponseCode(500);
            }
            UndertowLogger.REQUEST_LOGGER.errorf(t, "Undertow request failed %s", exchange);
            exchange.endExchange();
        }
    }

    /**
     * Returns the existing request channel, if it exists. Otherwise returns null
     *
     * @param exchange The http server exchange
     */
    public static StreamSourceChannel getExistingRequestChannel(final HttpServerExchange exchange) {
        return exchange.requestChannel;
    }
}
