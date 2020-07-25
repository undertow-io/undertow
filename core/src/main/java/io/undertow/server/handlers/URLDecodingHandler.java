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

package io.undertow.server.handlers;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.undertow.UndertowOptions;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.AttachmentKey;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.URLUtils;

/**
 * A handler that will decode the URL and query parameters to the specified charset.
 * <p>
 * This handler will not have any effect unless the {@link io.undertow.UndertowOptions#DECODE_URL} parameter is set to false.
 * <p>
 * This is not as efficient as using the parsers built in UTF-8 decoder. Unless you need to decode to something other
 * than UTF-8 you should rely on the parsers decoding instead.
 *
 * @author Stuart Douglas
 */
public class URLDecodingHandler implements HttpHandler {

    private static final ThreadLocal<StringBuilder> DECODING_BUFFER_CACHE = ThreadLocal.withInitial(StringBuilder::new);
    private static final AttachmentKey<Object> ALREADY_DECODED = AttachmentKey.create(Object.class);

    private final HttpHandler next;
    private final String charset;

    public URLDecodingHandler(final HttpHandler next, final String charset) {
        this.next = next;
        this.charset = charset;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (shouldDecode(exchange)) {
            final StringBuilder sb = getStringBuilderForDecoding(exchange);
            decodePath(exchange, charset, sb);
            decodeQueryString(exchange, charset, sb);
            decodePathTemplateMatch(exchange, charset, sb);
        }
        next.handleRequest(exchange);
    }

    // Returns true if the exchange should be decoded.  This method updates the ALREADY_DECODED
    // attachment so that subsequent invocations will always return false.
    private static boolean shouldDecode(final HttpServerExchange exchange) {
        return !exchange.getConnection().getUndertowOptions().get(UndertowOptions.DECODE_URL, true)
                && exchange.putAttachment(ALREADY_DECODED, Boolean.TRUE) == null;
    }

    private static void decodePath(HttpServerExchange exchange, String charset, StringBuilder sb) {
        final boolean decodeSlash = exchange.getConnection().getUndertowOptions().get(UndertowOptions.ALLOW_ENCODED_SLASH, false);
        exchange.setRequestPath(URLUtils.decode(exchange.getRequestPath(), charset, decodeSlash, false, sb));
        exchange.setRelativePath(URLUtils.decode(exchange.getRelativePath(), charset, decodeSlash, false, sb));
        exchange.setResolvedPath(URLUtils.decode(exchange.getResolvedPath(), charset, decodeSlash, false, sb));
    }

    private static void decodeQueryString(HttpServerExchange exchange, String charset, StringBuilder sb) {
        if (!exchange.getQueryString().isEmpty()) {
            final TreeMap<String, Deque<String>> newParams = new TreeMap<>();
            for (Map.Entry<String, Deque<String>> param : exchange.getQueryParameters().entrySet()) {
                final Deque<String> newValues = new ArrayDeque<>(param.getValue().size());
                for (String val : param.getValue()) {
                    newValues.add(URLUtils.decode(val, charset, true, true, sb));
                }
                newParams.put(URLUtils.decode(param.getKey(), charset, true, true, sb), newValues);
            }
            exchange.getQueryParameters().clear();
            exchange.getQueryParameters().putAll(newParams);
        }
    }

    private static void decodePathTemplateMatch(HttpServerExchange exchange, String charset, StringBuilder sb) {
        PathTemplateMatch pathTemplateMatch = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        if (pathTemplateMatch != null) {
            Map<String, String> parameters = pathTemplateMatch.getParameters();
            if (parameters != null) {
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    entry.setValue(URLUtils.decode(entry.getValue(), charset, true, false, sb));
                }
            }
        }
    }

    private static StringBuilder getStringBuilderForDecoding(HttpServerExchange exchange) {
        if (exchange.isInIoThread()) {
            // Unnecessary to clear the buffer here, URLUtils.decode updates the buffer length before usage.
            // We don't need to check the size after use because decoded size is bounded to the request line,
            // which cannot exceed one buffer.
            return DECODING_BUFFER_CACHE.get();
        }
        return new StringBuilder();
    }

    @Override
    public String toString() {
        return "url-decoding( " + charset + " )";
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "url-decoding";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String,Class<?>>singletonMap("charset", String.class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("charset");
        }

        @Override
        public String defaultParameter() {
            return "charset";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper(config.get("charset").toString());
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final String charset;

        private Wrapper(String charset) {
            this.charset = charset;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new URLDecodingHandler(handler, charset);
        }
    }

}
