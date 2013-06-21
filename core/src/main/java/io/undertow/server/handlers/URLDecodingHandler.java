package io.undertow.server.handlers;

import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * A handler that will decode the URL and query parameters to the specified charset.
 * <p/>
 * If you are using this handler you must set the {@link io.undertow.UndertowOptions#DECODE_URL} parameter to false.
 * <p/>
 * This is not as efficient as using the parsers built in UTF-8 decoder. Unless you need to decode to something other
 * than UTF-8 you should rely on the parsers decoding instead.
 *
 * @author Stuart Douglas
 */
public class URLDecodingHandler implements HttpHandler {

    private final HttpHandler next;
    private final String charset;

    public URLDecodingHandler(final HttpHandler next, final String charset) {
        this.next = next;
        this.charset = charset;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.setRequestPath(URLDecoder.decode(exchange.getRequestPath(), charset));
        exchange.setRelativePath(URLDecoder.decode(exchange.getRelativePath(), charset));
        exchange.setResolvedPath(URLDecoder.decode(exchange.getResolvedPath(), charset));
        if (!exchange.getQueryString().isEmpty()) {
            for (Map.Entry<String, Deque<String>> param : exchange.getQueryParameters().entrySet()) {
                final Deque<String> newVales = new ArrayDeque<String>(param.getValue().size());
                for (String val : param.getValue()) {
                    newVales.add(URLDecoder.decode(val, charset));
                }
                param.setValue(newVales);
            }
        }

    }
}
