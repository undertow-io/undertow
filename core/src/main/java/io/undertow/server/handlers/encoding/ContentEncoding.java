package io.undertow.server.handlers.encoding;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
public interface ContentEncoding {

    ContentEncoding IDENTITY = new ContentEncoding() {
        @Override
        public void setupContentEncoding(final HttpServerExchange exchange) {

        }
    };

    void setupContentEncoding(final HttpServerExchange exchange);

}
