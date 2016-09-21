/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server;

import io.undertow.UndertowLogger;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import io.undertow.util.URLUtils;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * This class provides the connector part of the {@link HttpServerExchange} API.
 * <p>
 * It contains methods that logically belong on the exchange, however should only be used
 * by connector implementations.
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
     * @param exchange The HTTP server exchange
     * @param buffers  The buffers to attach
     */
    public static void ungetRequestBytes(final HttpServerExchange exchange, PooledByteBuffer... buffers) {
        PooledByteBuffer[] existing = exchange.getAttachment(HttpServerExchange.BUFFERED_REQUEST_DATA);
        PooledByteBuffer[] newArray;
        if (existing == null) {
            newArray = new PooledByteBuffer[buffers.length];
            System.arraycopy(buffers, 0, newArray, 0, buffers.length);
        } else {
            newArray = new PooledByteBuffer[existing.length + buffers.length];
            System.arraycopy(existing, 0, newArray, 0, existing.length);
            System.arraycopy(buffers, 0, newArray, existing.length, buffers.length);
        }
        exchange.putAttachment(HttpServerExchange.BUFFERED_REQUEST_DATA, newArray); //todo: force some kind of wakeup?
        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                PooledByteBuffer[] bufs = exchange.getAttachment(HttpServerExchange.BUFFERED_REQUEST_DATA);
                if (bufs != null) {
                    for (PooledByteBuffer i : bufs) {
                        if(i != null) {
                            i.close();
                        }
                    }
                }
                nextListener.proceed();
            }
        });
    }

    public static void terminateRequest(final HttpServerExchange exchange) {
        exchange.terminateRequest();
    }

    public static void terminateResponse(final HttpServerExchange exchange) {
        exchange.terminateResponse();
    }

    public static void resetRequestChannel(final HttpServerExchange exchange) {
        exchange.resetRequestChannel();
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
                expires.setTime(expires.getTime() + cookie.getMaxAge() * 1000L);
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
        if (cookie.getComment() != null && !cookie.getComment().isEmpty()) {
            header.append("; Comment=");
            header.append(cookie.getComment());
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
                if (resumed) {
                    UndertowLogger.REQUEST_LOGGER.resumedAndDispatched();
                    exchange.setStatusCode(500);
                    exchange.endExchange();
                    return;
                }
                final Runnable dispatchTask = exchange.getDispatchTask();
                Executor executor = exchange.getDispatchExecutor();
                exchange.setDispatchExecutor(null);
                exchange.unDispatch();
                if (dispatchTask != null) {
                    executor = executor == null ? exchange.getConnection().getWorker() : executor;
                    executor.execute(dispatchTask);
                }
            } else if (!resumed) {
                exchange.endExchange();
            }
        } catch (Throwable t) {
            exchange.putAttachment(DefaultResponseListener.EXCEPTION, t);
            exchange.setInCall(false);
            if (!exchange.isResponseStarted()) {
                exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            }
            UndertowLogger.REQUEST_LOGGER.undertowRequestFailed(t, exchange);
            exchange.endExchange();
        }
    }


    /**
     * Sets the request path and query parameters, decoding to the requested charset.
     *
     * @param exchange    The exchange
     * @param encodedPath        The encoded path
     * @param charset     The charset
     */
    public static void setExchangeRequestPath(final HttpServerExchange exchange, final String encodedPath, final String charset, boolean decode, final boolean allowEncodedSlash, StringBuilder decodeBuffer) {
        boolean requiresDecode = false;
        for (int i = 0; i < encodedPath.length(); ++i) {
            char c = encodedPath.charAt(i);
            if (c == '?') {
                String part;
                String encodedPart = encodedPath.substring(0, i);
                if (requiresDecode) {
                    part = URLUtils.decode(encodedPart, charset, allowEncodedSlash, decodeBuffer);
                } else {
                    part = encodedPart;
                }
                exchange.setRequestPath(part);
                exchange.setRelativePath(part);
                exchange.setRequestURI(encodedPart);
                final String qs = encodedPath.substring(i + 1);
                exchange.setQueryString(qs);
                URLUtils.parseQueryString(qs, exchange, charset, decode);
                return;
            } else if(c == ';') {
                String part;
                String encodedPart = encodedPath.substring(0, i);
                if (requiresDecode) {
                    part = URLUtils.decode(encodedPart, charset, allowEncodedSlash, decodeBuffer);
                } else {
                    part = encodedPart;
                }
                exchange.setRequestPath(part);
                exchange.setRelativePath(part);
                for(int j = i; j < encodedPath.length(); ++j) {
                    if (encodedPath.charAt(j) == '?') {
                        exchange.setRequestURI(encodedPath.substring(0, j));
                        String pathParams = encodedPath.substring(i + 1, j);
                        URLUtils.parsePathParms(pathParams, exchange, charset, decode);
                        String qs = encodedPath.substring(j + 1);
                        exchange.setQueryString(qs);
                        URLUtils.parseQueryString(qs, exchange, charset, decode);
                        return;
                    }
                }
                exchange.setRequestURI(encodedPath);
                URLUtils.parsePathParms(encodedPath.substring(i + 1), exchange, charset, decode);
                return;
            } else if(c == '%' || c == '+') {
                requiresDecode = true;
            }
        }

        String part;
        if (requiresDecode) {
            part = URLUtils.decode(encodedPath, charset, allowEncodedSlash, decodeBuffer);
        } else {
            part = encodedPath;
        }
        exchange.setRequestPath(part);
        exchange.setRelativePath(part);
        exchange.setRequestURI(encodedPath);
    }


    /**
     * Returns the existing request channel, if it exists. Otherwise returns null
     *
     * @param exchange The http server exchange
     */
    public static StreamSourceChannel getExistingRequestChannel(final HttpServerExchange exchange) {
        return exchange.requestChannel;
    }

    public static boolean isEntityBodyAllowed(HttpServerExchange exchange){
        int code = exchange.getStatusCode();
        return isEntityBodyAllowed(code);
    }

    public static boolean isEntityBodyAllowed(int code) {
        if(code >= 100 && code < 200) {
            return false;
        }
        if(code == 204 || code == 304) {
            return false;
        }
        return true;
    }

    public static void updateResponseBytesSent(HttpServerExchange exchange, long bytes) {
        exchange.updateBytesSent(bytes);
    }

    public static ConduitStreamSinkChannel getConduitSinkChannel(HttpServerExchange exchange) {
        return exchange.getConnection().getSinkChannel();
    }
}
