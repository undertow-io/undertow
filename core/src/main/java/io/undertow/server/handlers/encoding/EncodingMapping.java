package io.undertow.server.handlers.encoding;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

/**
* @author Stuart Douglas
*/
final class EncodingMapping implements Comparable<EncodingMapping> {

    private final String name;
    private final ContentEncodingProvider encoding;
    private final int priority;
    private final Predicate<HttpServerExchange> allowed;

    EncodingMapping(final String name, final ContentEncodingProvider encoding, final int priority, final Predicate<HttpServerExchange> allowed) {
        this.name = name;
        this.encoding = encoding;
        this.priority = priority;
        this.allowed = allowed;
    }

    public String getName() {
        return name;
    }

    public ContentEncodingProvider getEncoding() {
        return encoding;
    }

    public int getPriority() {
        return priority;
    }

    public Predicate<HttpServerExchange> getAllowed() {
        return allowed;
    }

    @Override
    public int compareTo(final EncodingMapping o) {
        return priority - o.priority;
    }
}
