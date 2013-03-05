package io.undertow.server.handlers.resource;

import java.util.Date;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.TruePredicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.cache.ResponseCache;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.ETagUtils;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.MimeMappings;
import io.undertow.util.WorkerDispatcher;

/**
 * @author Stuart Douglas
 */
public class ResourceHandler implements HttpHandler {

    /**
     * If directory listing is enabled.
     */
    private volatile boolean directoryListingEnabled = false;

    /**
     * The mime mappings that are used to determine the content type.
     */
    private volatile MimeMappings mimeMappings = MimeMappings.DEFAULT;

    private volatile Predicate<HttpServerExchange> cachable = TruePredicate.instance();

    private volatile Predicate<HttpServerExchange> allowed = TruePredicate.instance();

    private volatile ResourceManager resourceManager;


    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        if (exchange.getRequestMethod().equals(Methods.GET) ||
                exchange.getRequestMethod().equals(Methods.POST)) {
            serveResource(exchange, true);
        } else if (exchange.getRequestMethod().equals(Methods.HEAD)) {
            serveResource(exchange, false);
        } else {
            exchange.setResponseCode(405);
            exchange.endExchange();
        }
    }

    private void serveResource(final HttpServerExchange exchange, final boolean sendContent) {

        if(DirectoryUtils.sendRequestedBlobs(exchange)) {
            return;
        }

        if (!allowed.resolve(exchange)) {
            exchange.setResponseCode(403);
            exchange.endExchange();
        }

        ResponseCache cache = exchange.getAttachment(ResponseCache.ATTACHMENT_KEY);
        if (cache != null && cachable.resolve(exchange)) {
            if (cache.tryServeResponse()) {
                return;
            }
        }

        //we now dispatch to a worker thread
        //as resource manager methods are potentially blocking
        WorkerDispatcher.dispatch(exchange, new Runnable() {
            @Override
            public void run() {
                Resource resource = resourceManager.getResource(exchange.getRelativePath());
                if (resource == null) {
                    exchange.setResponseCode(404);
                    exchange.endExchange();
                    return;
                }

                if (resource.isDirectory()) {
                    DirectoryUtils.renderDirectoryListing(exchange, resource);
                    return;
                }

                final ETag etag = resource.getETag();
                final Date lastModified = resource.getLastModified();
                if (!ETagUtils.handleIfMatch(exchange, etag, false) ||
                        !DateUtils.handleIfUnmodifiedSince(exchange, lastModified)) {
                    exchange.setResponseCode(412);
                    exchange.endExchange();
                    return;
                }
                if (!ETagUtils.handleIfNoneMatch(exchange, etag, true) ||
                        !DateUtils.handleIfModifiedSince(exchange, lastModified)) {
                    exchange.setResponseCode(304);
                    exchange.endExchange();
                    return;
                }
                //todo: handle range requests
                //we are going to proceed. Set the appropriate headers
                final String contentType = resource.getContentType(mimeMappings);
                if (contentType != null) {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
                } else {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
                }
                if (lastModified != null) {
                    exchange.getResponseHeaders().put(Headers.LAST_MODIFIED, DateUtils.toDateString(lastModified));
                }
                if (etag != null) {
                    exchange.getResponseHeaders().put(Headers.CONTENT_LANGUAGE, etag.toString());
                }
                Long contentLength = resource.getContentLength();
                if (contentLength != null) {
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, contentLength.toString());
                }
                if(!sendContent) {
                    exchange.endExchange();
                } else {
                    resource.serve(exchange);
                }
            }
        });


    }

    public boolean isDirectoryListingEnabled() {
        return directoryListingEnabled;
    }

    public ResourceHandler setDirectoryListingEnabled(final boolean directoryListingEnabled) {
        this.directoryListingEnabled = directoryListingEnabled;
        return this;
    }

    public MimeMappings getMimeMappings() {
        return mimeMappings;
    }

    public ResourceHandler setMimeMappings(final MimeMappings mimeMappings) {
        this.mimeMappings = mimeMappings;
        return this;
    }

    public Predicate<HttpServerExchange> getCachable() {
        return cachable;
    }

    public ResourceHandler setCachable(final Predicate<HttpServerExchange> cachable) {
        this.cachable = cachable;
        return this;
    }

    public Predicate<HttpServerExchange> getAllowed() {
        return allowed;
    }

    public ResourceHandler setAllowed(final Predicate<HttpServerExchange> allowed) {
        this.allowed = allowed;
        return this;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public ResourceHandler setResourceManager(final ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        return this;
    }
}
