package io.undertow.server.handlers.cache;

/**
 * Handler that marks a response as cacheable, based on a {@link io.undertow.predicate.Predicate}
 *
 * If a matching entry is found in the cache then that entry will be served and the request will be
 * terminated, otherwise the next handler will be invoked, and the response will be cached.
 *
 * @author Stuart Douglas
 */
public class CacheResponseHandler {
}
