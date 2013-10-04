package io.undertow.server;

import io.undertow.UndertowLogger;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;

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
                StringBuilder builder = new StringBuilder();
                builder.append(getCookieString(entry.getValue()));
                exchange.getResponseHeaders().add(Headers.SET_COOKIE, builder.toString());
            }
        }
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
            if (exchange.isDispatched()) {
                final Runnable dispatchTask = exchange.getDispatchTask();
                Executor executor = exchange.getDispatchExecutor();
                exchange.unDispatch();
                if (dispatchTask != null) {
                    executor = executor == null ? exchange.getConnection().getWorker() : executor;
                    executor.execute(dispatchTask);
                }
            } else {
                exchange.endExchange();
            }
        } catch (Throwable t) {
            exchange.setInCall(false);
            if (!exchange.isResponseStarted()) {
                exchange.setResponseCode(500);
            }
            UndertowLogger.REQUEST_LOGGER.errorf(t, "Blocking request failed %s", exchange);
            exchange.endExchange();
        }
    }
}
