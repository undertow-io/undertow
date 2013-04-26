package io.undertow.server.handlers.encoding;

import io.undertow.predicate.Predicate;

/**
* @author Stuart Douglas
*/
final class EncodingMapping implements Comparable<EncodingMapping> {

    private final String name;
    private final ContentEncodingProvider encoding;
    private final int priority;
    private final Predicate allowed;

    EncodingMapping(final String name, final ContentEncodingProvider encoding, final int priority, final Predicate allowed) {
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

    public Predicate getAllowed() {
        return allowed;
    }

    @Override
    public int compareTo(final EncodingMapping o) {
        return priority - o.priority;
    }
}
