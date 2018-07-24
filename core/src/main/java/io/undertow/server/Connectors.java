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
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.DateUtils;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.LegacyCookieSupport;
import io.undertow.util.ParameterLimitException;
import io.undertow.util.StatusCodes;
import io.undertow.util.URLUtils;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * This class provides the connector part of the {@link HttpServerExchange} API.
 * <p>
 * It contains methods that logically belong on the exchange, however should only be used
 * by connector implementations.
 *
 * @author Stuart Douglas
 */
public class Connectors {

    private static final boolean[] ALLOWED_TOKEN_CHARACTERS = new boolean[256];

    static {
        for(int i = 0; i < ALLOWED_TOKEN_CHARACTERS.length; ++i) {
            if((i >='0' && i <= '9') ||
                    (i >='a' && i <= 'z') ||
                    (i >='A' && i <= 'Z')) {
                ALLOWED_TOKEN_CHARACTERS[i] = true;
            } else {
                switch (i) {
                    case '!':
                    case '#':
                    case '$':
                    case '%':
                    case '&':
                    case '\'':
                    case '*':
                    case '+':
                    case '-':
                    case '.':
                    case '^':
                    case '_':
                    case '`':
                    case '|':
                    case '~': {
                        ALLOWED_TOKEN_CHARACTERS[i] = true;
                        break;
                    }
                    default:
                        ALLOWED_TOKEN_CHARACTERS[i] = false;
                }
            }
        }
    }
    /**
     * Flattens the exchange cookie map into the response header map. This should be called by a
     * connector just before the response is started.
     *
     * @param exchange The server exchange
     */
    public static void flattenCookies(final HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getResponseCookiesInternal();
        boolean enableRfc6265Validation = exchange.getConnection().getUndertowOptions().get(UndertowOptions.ENABLE_RFC6265_COOKIE_VALIDATION, UndertowOptions.DEFAULT_ENABLE_RFC6265_COOKIE_VALIDATION);
        if (cookies != null) {
            for (Map.Entry<String, Cookie> entry : cookies.entrySet()) {
                exchange.getResponseHeaders().add(Headers.SET_COOKIE, getCookieString(entry.getValue(), enableRfc6265Validation));
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

    private static String getCookieString(final Cookie cookie, boolean enableRfc6265Validation) {
        if(enableRfc6265Validation) {
            return addRfc6265ResponseCookieToExchange(cookie);
        } else {
            switch (LegacyCookieSupport.adjustedCookieVersion(cookie)) {
                case 0:
                    return addVersion0ResponseCookieToExchange(cookie);
                case 1:
                default:
                    return addVersion1ResponseCookieToExchange(cookie);
            }
        }
    }

    public static void setRequestStartTime(HttpServerExchange exchange) {
        exchange.setRequestStartTime(System.nanoTime());
    }

    public static void setRequestStartTime(HttpServerExchange existing, HttpServerExchange newExchange) {
        newExchange.setRequestStartTime(existing.getRequestStartTime());
    }

    private static String addRfc6265ResponseCookieToExchange(final Cookie cookie) {
        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        if(cookie.getValue() != null) {
            header.append(cookie.getValue());
        }
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
            // Microsoft IE and Microsoft Edge don't understand Max-Age so send
            // expires as well. Without this, persistent cookies fail with those
            // browsers. They do understand Expires, even with V1 cookies.
            // So, we add Expires header when Expires is not explicitly specified.
            if (cookie.getExpires() == null) {
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
        }
        if (cookie.getExpires() != null) {
            header.append("; Expires=");
            header.append(DateUtils.toDateString(cookie.getExpires()));
        }
        if (cookie.getComment() != null && !cookie.getComment().isEmpty()) {
            header.append("; Comment=");
            header.append(cookie.getComment());
        }
        if (cookie.isSameSite()) {
            if (cookie.getSameSiteMode() != null && !cookie.getSameSiteMode().isEmpty()) {
                header.append("; SameSite=");
                header.append(cookie.getSameSiteMode());
            } else {
                header.append("; SameSite");
            }
        }
        return header.toString();
    }

    private static String addVersion0ResponseCookieToExchange(final Cookie cookie) {
        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        if(cookie.getValue() != null) {
            LegacyCookieSupport.maybeQuote(header, cookie.getValue());
        }

        if (cookie.getPath() != null) {
            header.append("; path=");
            LegacyCookieSupport.maybeQuote(header, cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            header.append("; domain=");
            LegacyCookieSupport.maybeQuote(header, cookie.getDomain());
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
        if (cookie.isSameSite()) {
            if (cookie.getSameSiteMode() != null && !cookie.getSameSiteMode().isEmpty()) {
                header.append("; SameSite=");
                header.append(cookie.getSameSiteMode());
            } else {
                header.append("; SameSite");
            }
        }
        return header.toString();

    }

    private static String addVersion1ResponseCookieToExchange(final Cookie cookie) {

        final StringBuilder header = new StringBuilder(cookie.getName());
        header.append("=");
        if(cookie.getValue() != null) {
            LegacyCookieSupport.maybeQuote(header, cookie.getValue());
        }
        header.append("; Version=1");
        if (cookie.getPath() != null) {
            header.append("; Path=");
            LegacyCookieSupport.maybeQuote(header, cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            header.append("; Domain=");
            LegacyCookieSupport.maybeQuote(header, cookie.getDomain());
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
            // Microsoft IE and Microsoft Edge don't understand Max-Age so send
            // expires as well. Without this, persistent cookies fail with those
            // browsers. They do understand Expires, even with V1 cookies.
            // So, we add Expires header when Expires is not explicitly specified.
            if (cookie.getExpires() == null) {
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
        }
        if (cookie.getExpires() != null) {
            header.append("; Expires=");
            header.append(DateUtils.toDateString(cookie.getExpires()));
        }
        if (cookie.getComment() != null && !cookie.getComment().isEmpty()) {
            header.append("; Comment=");
            LegacyCookieSupport.maybeQuote(header, cookie.getComment());
        }
        if (cookie.isSameSite()) {
            if (cookie.getSameSiteMode() != null && !cookie.getSameSiteMode().isEmpty()) {
                header.append("; SameSite=");
                header.append(cookie.getSameSiteMode());
            } else {
                header.append("; SameSite");
            }
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
                    try {
                        executor.execute(dispatchTask);
                    } catch (RejectedExecutionException e) {
                        UndertowLogger.REQUEST_LOGGER.debug("Failed to dispatch to worker", e);
                        exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
                        exchange.endExchange();
                    }
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
            if(t instanceof IOException) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException((IOException) t);
            } else {
                UndertowLogger.REQUEST_LOGGER.undertowRequestFailed(t, exchange);
            }
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
    @Deprecated
    public static void setExchangeRequestPath(final HttpServerExchange exchange, final String encodedPath, final String charset, boolean decode, final boolean allowEncodedSlash, StringBuilder decodeBuffer) {
        try {
            setExchangeRequestPath(exchange, encodedPath, charset, decode, allowEncodedSlash, decodeBuffer, exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_PARAMETERS, UndertowOptions.DEFAULT_MAX_PARAMETERS));
        } catch (ParameterLimitException e) {
            throw new RuntimeException(e);
        }
    }
        /**
         * Sets the request path and query parameters, decoding to the requested charset.
         *
         * @param exchange    The exchange
         * @param encodedPath        The encoded path
         * @param charset     The charset
         */
    public static void setExchangeRequestPath(final HttpServerExchange exchange, final String encodedPath, final String charset, boolean decode, final boolean allowEncodedSlash, StringBuilder decodeBuffer, int maxParameters) throws ParameterLimitException {
        boolean requiresDecode = false;
        for (int i = 0; i < encodedPath.length(); ++i) {
            char c = encodedPath.charAt(i);
            if (c == '?') {
                String part;
                String encodedPart = encodedPath.substring(0, i);
                if (requiresDecode) {
                    part = URLUtils.decode(encodedPart, charset, allowEncodedSlash,false, decodeBuffer);
                } else {
                    part = encodedPart;
                }
                exchange.setRequestPath(part);
                exchange.setRelativePath(part);
                exchange.setRequestURI(encodedPart);
                final String qs = encodedPath.substring(i + 1);
                exchange.setQueryString(qs);
                URLUtils.parseQueryString(qs, exchange, charset, decode, maxParameters);
                return;
            } else if(c == ';') {
                String part;
                String encodedPart = encodedPath.substring(0, i);
                if (requiresDecode) {
                    part = URLUtils.decode(encodedPart, charset, allowEncodedSlash, false, decodeBuffer);
                } else {
                    part = encodedPart;
                }
                exchange.setRequestPath(part);
                exchange.setRelativePath(part);
                for(int j = i; j < encodedPath.length(); ++j) {
                    if (encodedPath.charAt(j) == '?') {
                        exchange.setRequestURI(encodedPath.substring(0, j));
                        String pathParams = encodedPath.substring(i + 1, j);
                        URLUtils.parsePathParams(pathParams, exchange, charset, decode, maxParameters);
                        String qs = encodedPath.substring(j + 1);
                        exchange.setQueryString(qs);
                        URLUtils.parseQueryString(qs, exchange, charset, decode, maxParameters);
                        return;
                    }
                }
                exchange.setRequestURI(encodedPath);
                URLUtils.parsePathParams(encodedPath.substring(i + 1), exchange, charset, decode, maxParameters);
                return;
            } else if(c == '%' || c == '+') {
                requiresDecode = true;
            }
        }

        String part;
        if (requiresDecode) {
            part = URLUtils.decode(encodedPath, charset, allowEncodedSlash, false, decodeBuffer);
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

    /**
     * Verifies that the contents of the HttpString are a valid token according to rfc7230.
     * @param header The header to verify
     */
    public static void verifyToken(HttpString header) {
        int length = header.length();
        for(int i = 0; i < length; ++i) {
            byte c = header.byteAt(i);
            if(!ALLOWED_TOKEN_CHARACTERS[c]) {
                throw UndertowMessages.MESSAGES.invalidToken(c);
            }
        }
    }

    /**
     * Returns true if the token character is valid according to rfc7230
     */
    public static boolean isValidTokenCharacter(byte c) {
        return ALLOWED_TOKEN_CHARACTERS[c];
    }


    /**
     * Verifies that the provided request headers are valid according to rfc7230. In particular:
     * - At most one content-length or transfer encoding
     */
    public static boolean areRequestHeadersValid(HeaderMap headers) {
        HeaderValues te = headers.get(Headers.TRANSFER_ENCODING);
        HeaderValues cl = headers.get(Headers.CONTENT_LENGTH);
        if(te != null && cl != null) {
            return false;
        } else if(te != null && te.size() > 1) {
            return false;
        } else if(cl != null && cl.size() > 1) {
            return false;
        }
        return true;
    }
}
