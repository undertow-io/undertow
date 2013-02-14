package io.undertow.server.handlers.encoding;

import java.util.List;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Headers;
import org.xnio.conduits.StreamSinkConduit;

/**
 * An attachment that provides information about the current content encoding that will be chosen for the response
 *
 * @author Stuart Douglas
 */
public class ContentEncoding implements ConduitWrapper<StreamSinkConduit> {

    public static final AttachmentKey<ContentEncoding> CONENT_ENCODING = AttachmentKey.create(ContentEncoding.class);

    private final HttpServerExchange exchange;
    private final List<EncodingMapping> encodings;


    public ContentEncoding(final HttpServerExchange exchange, final List<EncodingMapping> encodings) {
        this.exchange = exchange;
        this.encodings = encodings;
    }

    /**
     * @return The content encoding that will be set, given the current state of the HttpServerExchange
     */
    public String getCurrentContentEncoding() {
        for (EncodingMapping encoding : encodings) {
            if (encoding.getAllowed().resolve(exchange)) {
                return encoding.getName();
            }
        }
        return Headers.IDENTITY.toString();
    }

    @Override
    public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
        for (EncodingMapping encoding : encodings) {
            if (encoding.getAllowed().resolve(exchange)) {
                exchange.getResponseHeaders().put(Headers.CONTENT_ENCODING, encoding.getName());
                return encoding.getEncoding().getResponseWrapper().wrap(factory, exchange);
            }
        }
        return factory.create();
    }
}
