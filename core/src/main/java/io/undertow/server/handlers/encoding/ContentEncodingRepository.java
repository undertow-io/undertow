package io.undertow.server.handlers.encoding;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;
import io.undertow.util.QValueParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 *
 * @author Stuart Douglas
 */
public class ContentEncodingRepository {

    public static final String IDENTITY = "identity";

    private final Map<String, EncodingMapping> encodingMap = new CopyOnWriteMap<String, EncodingMapping>();

    /**
     * Gets all allow
     * @param exchange
     * @return
     */
    public AllowedContentEncodings getContentEncodings(final HttpServerExchange exchange) {
        final List<String> res = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING);
        if (res == null || res.isEmpty()) {
            return null;
        }
        final List<EncodingMapping> resultingMappings = new ArrayList<EncodingMapping>();
        final List<List<QValueParser.QValueResult>> found = QValueParser.parse(res);
        for (List<QValueParser.QValueResult> result : found) {
            List<EncodingMapping> available = new ArrayList<EncodingMapping>();
            boolean includesIdentity = false;
            boolean isQValue0 = false;

            for (final QValueParser.QValueResult value : result) {
                EncodingMapping encoding;
                if (value.getValue().equals("*")) {
                    includesIdentity = true;
                    encoding = new EncodingMapping(IDENTITY, ContentEncodingProvider.IDENTITY, 0, Predicates.truePredicate());
                } else {
                    encoding = encodingMap.get(value.getValue());
                }
                if (value.isQValueZero()) {
                    isQValue0 = true;
                }
                if (encoding != null) {
                    available.add(encoding);
                }
            }
            if (isQValue0) {
                if (resultingMappings.isEmpty()) {
                    if (includesIdentity) {
                        return new AllowedContentEncodings(exchange, Collections.<EncodingMapping>emptyList());
                    } else {
                        return null;
                    }
                }
            } else if (!available.isEmpty()) {
                Collections.sort(available, Collections.reverseOrder());
                resultingMappings.addAll(available);
            }
        }
        if (!resultingMappings.isEmpty()) {
            return new AllowedContentEncodings(exchange, resultingMappings);
        }
        return null;
    }

    public synchronized ContentEncodingRepository addEncodingHandler(final String encoding, final ContentEncodingProvider encoder, int priority) {
        addEncodingHandler(encoding, encoder, priority, Predicates.truePredicate());
        return this;
    }

    public synchronized ContentEncodingRepository addEncodingHandler(final String encoding, final ContentEncodingProvider encoder, int priority, final Predicate enabledPredicate) {
        this.encodingMap.put(encoding, new EncodingMapping(encoding, encoder, priority, enabledPredicate));
        return this;
    }

    public synchronized ContentEncodingRepository removeEncodingHandler(final String encoding) {
        encodingMap.remove(encoding);
        return this;
    }

}
