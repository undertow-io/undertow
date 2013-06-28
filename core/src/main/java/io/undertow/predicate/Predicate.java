package io.undertow.predicate;

import java.util.Map;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * A predicate.
 *
 * This is mainly uses by handlers as a way to decide if a request should have certain
 * processing applied, based on the given conditions.
 *
 * @author Stuart Douglas
 */
public interface Predicate {

    /**
     * Attachment key that can be used to store additional predicate context that allows the predicates to store
     * additional information. For example a predicate that matches on a regular expression can place additional
     * information about match groups into the predicate context.
     *
     * Predicates must not rely on this attachment being present, it will only be present if the predicate is being
     * used in a situation where this information may be required by later handlers.
     *
     */
    AttachmentKey<Map<String, Object>> PREDICATE_CONTEXT = AttachmentKey.create(Map.class);

    boolean resolve(final HttpServerExchange value);

}
