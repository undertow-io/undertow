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

package io.undertow;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.predicate.PredicatesHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.JvmRouteHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.SetErrorHandler;
import io.undertow.server.handlers.AccessControlListHandler;
import io.undertow.server.handlers.LearningPushHandler;
import io.undertow.server.handlers.DateHandler;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.server.handlers.ExceptionHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.HttpContinueAcceptingHandler;
import io.undertow.server.handlers.HttpContinueReadHandler;
import io.undertow.server.handlers.HttpTraceHandler;
import io.undertow.server.handlers.IPAddressAccessControlHandler;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.PathTemplateHandler;
import io.undertow.server.handlers.PredicateContextHandler;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.ProxyPeerAddressHandler;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.handlers.RequestDumpingHandler;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.server.handlers.ResponseRateLimitingHandler;
import io.undertow.server.handlers.SetAttributeHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.URLDecodingHandler;
import io.undertow.server.handlers.builder.PredicatedHandler;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.handlers.sse.ServerSentEventConnectionCallback;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class with convenience methods for dealing with handlers
 *
 * @author Stuart Douglas
 */
public class Handlers {

    /**
     * Creates a new path handler, with the default handler specified
     *
     * @param defaultHandler The default handler
     * @return A new path handler
     */
    public static PathHandler path(final HttpHandler defaultHandler) {
        return new PathHandler(defaultHandler);
    }

    /**
     * Creates a new path handler
     *
     * @return A new path handler
     */
    public static PathHandler path() {
        return new PathHandler();
    }

    /**
     *
     * @return a new path template handler
     */
    public static PathTemplateHandler pathTemplate() {
        return new PathTemplateHandler();
    }

    /**
     *
     * @param rewriteQueryParams If the query params should be rewritten
     * @return The routing handler
     */
    public static RoutingHandler routing(boolean rewriteQueryParams) {
        return new RoutingHandler(rewriteQueryParams);
    }

    /**
     *
     * @return a new routing handler
     */
    public static RoutingHandler routing() {
        return new RoutingHandler();
    }

    /**
     *
     * @param rewriteQueryParams If the query params should be rewritten
     * @return The path template handler
     */
    public static PathTemplateHandler pathTemplate(boolean rewriteQueryParams) {
        return new PathTemplateHandler(rewriteQueryParams);
    }


    /**
     * Creates a new virtual host handler
     *
     * @return A new virtual host handler
     */
    public static NameVirtualHostHandler virtualHost() {
        return new NameVirtualHostHandler();
    }

    /**
     * Creates a new virtual host handler using the provided default handler
     *
     * @return A new virtual host handler
     */
    public static NameVirtualHostHandler virtualHost(final HttpHandler defaultHandler) {
        return new NameVirtualHostHandler().setDefaultHandler(defaultHandler);
    }

    /**
     * Creates a new virtual host handler that uses the provided handler as the root handler for the given hostnames.
     *
     * @param hostHandler The host handler
     * @param hostnames   The host names
     * @return A new virtual host handler
     */
    public static NameVirtualHostHandler virtualHost(final HttpHandler hostHandler, String... hostnames) {
        NameVirtualHostHandler handler = new NameVirtualHostHandler();
        for (String host : hostnames) {
            handler.addHost(host, hostHandler);
        }
        return handler;
    }

    /**
     * Creates a new virtual host handler that uses the provided handler as the root handler for the given hostnames.
     *
     * @param defaultHandler The default handler
     * @param hostHandler    The host handler
     * @param hostnames      The host names
     * @return A new virtual host handler
     */
    public static NameVirtualHostHandler virtualHost(final HttpHandler defaultHandler, final HttpHandler hostHandler, String... hostnames) {
        return virtualHost(hostHandler, hostnames).setDefaultHandler(defaultHandler);
    }

    /**
     * @param sessionHandler The web socket session handler
     * @return The web socket handler
     */
    public static WebSocketProtocolHandshakeHandler websocket(final WebSocketConnectionCallback sessionHandler) {
        return new WebSocketProtocolHandshakeHandler(sessionHandler);
    }

    /**
     * @param sessionHandler The web socket session handler
     * @param next           The handler to invoke if the web socket connection fails
     * @return The web socket handler
     */
    public static WebSocketProtocolHandshakeHandler websocket(final WebSocketConnectionCallback sessionHandler, final HttpHandler next) {
        return new WebSocketProtocolHandshakeHandler(sessionHandler, next);
    }

    /**
     * A handler for server sent events
     *
     *
     * @param callback The server sent events callback
     * @return A new server sent events handler
     */
    public static ServerSentEventHandler serverSentEvents(ServerSentEventConnectionCallback callback) {
        return new ServerSentEventHandler(callback);
    }

    /**
     * A handler for server sent events
     *
     * @return A new server sent events handler
     */
    public static ServerSentEventHandler serverSentEvents() {
        return new ServerSentEventHandler();
    }
    /**
     * Return a new resource handler
     *
     * @param resourceManager The resource manager to use
     * @return A new resource handler
     */
    public static ResourceHandler resource(final ResourceManager resourceManager) {
        return new ResourceHandler(resourceManager).setDirectoryListingEnabled(false);
    }

    /**
     * Returns a new redirect handler
     *
     * @param location The redirect location
     * @return A new redirect handler
     */
    public static RedirectHandler redirect(final String location) {
        return new RedirectHandler(location);
    }

    /**
     * Returns a new HTTP trace handler. This handler will handle HTTP TRACE
     * requests as per the RFC.
     * <p>
     * WARNING: enabling trace requests may leak information, in general it is recommended that
     * these be disabled for security reasons.
     *
     * @param next The next handler in the chain
     * @return A HTTP trace handler
     */
    public static HttpTraceHandler trace(final HttpHandler next) {
        return new HttpTraceHandler(next);
    }

    /**
     * Returns a new HTTP handler that sets the Date: header.
     *
     * This is no longer necessary, as it is handled by the connectors directly.
     *
     * @param next The next handler in the chain
     * @return A new date handler
     */
    @Deprecated
    public static DateHandler date(final HttpHandler next) {
        return new DateHandler(next);
    }

    /**
     * Returns a new predicate handler, that will delegate to one of the two provided handlers based on the value of the
     * provided predicate.
     *
     * @param predicate    The predicate
     * @param trueHandler  The handler that will be executed if the predicate is true
     * @param falseHandler The handler that will be exected if the predicate is false
     * @return A new predicate handler
     * @see Predicate
     * @see io.undertow.predicate.Predicates
     */
    public static PredicateHandler predicate(final Predicate predicate, final HttpHandler trueHandler, final HttpHandler falseHandler) {
        return new PredicateHandler(predicate, trueHandler, falseHandler);
    }

    /**
     * @param next The next handler
     * @return a handler that sets up a new predicate context
     */
    public static HttpHandler predicateContext(HttpHandler next) {
        return new PredicateContextHandler(next);
    }

    public static PredicatesHandler predicates(final List<PredicatedHandler> handlers, HttpHandler next) {
        final PredicatesHandler predicatesHandler = new PredicatesHandler(next);
        for(PredicatedHandler handler : handlers) {
            predicatesHandler.addPredicatedHandler(handler);
        }
        return predicatesHandler;
    }

    /**
     * Returns a handler that sets a response header
     *
     * @param next        The next handler in the chain
     * @param headerName  The name of the header
     * @param headerValue The header value
     * @return A new set header handler
     */
    public static SetHeaderHandler header(final HttpHandler next, final String headerName, final String headerValue) {
        return new SetHeaderHandler(next, headerName, headerValue);
    }


    /**
     * Returns a handler that sets a response header
     *
     * @param next        The next handler in the chain
     * @param headerName  The name of the header
     * @param headerValue The header value
     * @return A new set header handler
     */
    public static SetHeaderHandler header(final HttpHandler next, final String headerName, final ExchangeAttribute headerValue) {
        return new SetHeaderHandler(next, headerName, headerValue);
    }


    /**
     * Returns a new handler that can allow or deny access to a resource based on IP address
     *
     * @param next         The next handler in the chain
     * @param defaultAllow Determine if a non-matching address will be allowed by default
     * @return A new IP access control handler
     */
    public static final IPAddressAccessControlHandler ipAccessControl(final HttpHandler next, boolean defaultAllow) {
        return new IPAddressAccessControlHandler(next).setDefaultAllow(defaultAllow);
    }

    /**
     * Returns a new handler that can allow or deny access to a resource based an at attribute of the exchange
     *
     * @param next         The next handler in the chain
     * @param defaultAllow Determine if a non-matching user agent will be allowed by default
     * @return A new user agent access control handler
     */
    public static final AccessControlListHandler acl(final HttpHandler next, boolean defaultAllow, ExchangeAttribute attribute) {
        return new AccessControlListHandler(next, attribute).setDefaultAllow(defaultAllow);
    }

    /**
     * A handler that automatically handles HTTP 100-continue responses, by sending a continue
     * response when the first attempt is made to read from the request channel.
     *
     * @param next The next handler in the chain
     * @return A new continue handler
     */
    public static final HttpContinueReadHandler httpContinueRead(final HttpHandler next) {
        return new HttpContinueReadHandler(next);
    }

    /**
     * Returns a handler that sends back a HTTP 100 continue response if the given predicate resolves to true.
     *
     * This handler differs from the one returned by {@link #httpContinueRead(io.undertow.server.HttpHandler)} in
     * that it will eagerly send the response, and not wait for the first read attempt.
     *
     * @param next The next handler
     * @param accept The predicate used to determine if the request should be accepted
     * @return The accepting handler
     */
    public static final HttpContinueAcceptingHandler httpContinueAccepting(final HttpHandler next, final Predicate accept) {
        return new HttpContinueAcceptingHandler(next, accept);
    }

    /**
     * Returns a handler that sends back a HTTP 100 continue response to all requests.
     *
     * This handler differs from the one returned by {@link #httpContinueRead(io.undertow.server.HttpHandler)} in
     * that it will eagerly send the response, and not wait for the first read attempt.
     *
     * @param next The next handler
     * @return The accepting handler
     */
    public static final HttpContinueAcceptingHandler httpContinueAccepting(final HttpHandler next) {
        return new HttpContinueAcceptingHandler(next);
    }

    /**
     * A handler that will decode the URL, query parameters and to the specified charset.
     * <p>
     * If you are using this handler you must set the {@link io.undertow.UndertowOptions#DECODE_URL} parameter to false.
     * <p>
     * This is not as efficient as using the parsers built in UTF-8 decoder. Unless you need to decode to something other
     * than UTF-8 you should rely on the parsers decoding instead.
     *
     * @param next    The next handler in the chain
     * @param charset The charset to decode to
     * @return a new url decoding handler
     */
    public static final URLDecodingHandler urlDecoding(final HttpHandler next, final String charset) {
        return new URLDecodingHandler(next, charset);
    }

    /**
     * Returns an attribute setting handler that can be used to set an arbitrary attribute on the exchange.
     * This includes functions such as adding and removing headers etc.
     *
     * @param next        The next handler
     * @param attribute   The attribute to set, specified as a string presentation of an {@link io.undertow.attribute.ExchangeAttribute}
     * @param value       The value to set, specified an a string representation of an {@link io.undertow.attribute.ExchangeAttribute}
     * @param classLoader The class loader to use to parser the exchange attributes
     * @return The handler
     */
    public static SetAttributeHandler setAttribute(final HttpHandler next, final String attribute, final String value, final ClassLoader classLoader) {
        return new SetAttributeHandler(next, attribute, value, classLoader);
    }

    /**
     * Creates the set of handlers that are required to perform a simple rewrite.
     * @param condition The rewrite condition
     * @param target The rewrite target if the condition matches
     * @param next The next handler
     * @return
     */
    public static HttpHandler rewrite(final String condition, final String target, final ClassLoader classLoader, final HttpHandler next) {
        return predicateContext(predicate(PredicateParser.parse(condition, classLoader), setAttribute(next, "%R", target, classLoader), next));
    }

    /**
     * Returns a new handler that decodes the URL and query parameters into the specified charset, assuming it
     * has not already been done by the connector. For this handler to take effect the parameter
     * {@link UndertowOptions#DECODE_URL} must have been set to false.
     *
     * @param charset The charset to decode
     * @param next The next handler
     * @return A handler that decodes the URL
     */
    public static HttpHandler urlDecodingHandler(final String charset, final HttpHandler next) {
        return new URLDecodingHandler(next, charset);
    }


    /**
     * Returns a new handler that can be used to wait for all requests to finish before shutting down the server gracefully.
     *
     * @param next The next http handler
     * @return The graceful shutdown handler
     */
    public static GracefulShutdownHandler gracefulShutdown(HttpHandler next) {
        return new GracefulShutdownHandler(next);
    }

    /**
     * Returns a new handler that sets the peer address based on the X-Forwarded-For and
     * X-Forwarded-Proto header
     * @param next The next http handler
     * @return The handler
     */
    public static ProxyPeerAddressHandler proxyPeerAddress(HttpHandler next) {
        return new ProxyPeerAddressHandler(next);
    }

    /**
     * Handler that appends the JVM route to the session cookie
     * @param sessionCookieName The session cookie name
     * @param jvmRoute The JVM route to append
     * @param next The next handler
     * @return The handler
     */
    public static JvmRouteHandler jvmRoute(final String sessionCookieName, final String jvmRoute, HttpHandler next) {
        return new JvmRouteHandler(next, sessionCookieName, jvmRoute);
    }

    /**
     * Returns a handler that limits the maximum number of requests that can run at a time.
     *
     * @param maxRequest The maximum number of requests
     * @param queueSize  The maximum number of queued requests
     * @param next       The next handler
     * @return           The handler
     */
    public static RequestLimitingHandler requestLimitingHandler(final int maxRequest, final int queueSize, HttpHandler next) {
        return new RequestLimitingHandler(maxRequest, queueSize, next);
    }

    /**
     * Returns a handler that limits the maximum number of requests that can run at a time.
     *
     * @param requestLimit The request limit object that can be shared between handlers, to apply the same limits across multiple handlers
     * @param next         The next handler
     * @return             The handler
     */
    public static RequestLimitingHandler requestLimitingHandler(final RequestLimit requestLimit, HttpHandler next) {
        return new RequestLimitingHandler(requestLimit, next);
    }

    /**
     * Returns a handler that can act as a load balancing reverse proxy.
     *
     * @param proxyClient The proxy client to use to connect to the remote server
     * @param maxRequestTime The maximum amount of time a request can be in progress before it is forcibly closed
     * @param next The next handler to invoke if the proxy client does not know how to proxy the request
     * @return The proxy handler
     */
    public static ProxyHandler proxyHandler(ProxyClient proxyClient, int maxRequestTime, HttpHandler next) {
        return ProxyHandler.builder().setProxyClient(proxyClient).setNext(next).setMaxRequestTime(maxRequestTime).build();
    }
    /**
     * Returns a handler that can act as a load balancing reverse proxy.
     *
     * @param proxyClient The proxy client to use to connect to the remote server
     * @param next The next handler to invoke if the proxy client does not know how to proxy the request
     * @return The proxy handler
     */
    public static ProxyHandler proxyHandler(ProxyClient proxyClient, HttpHandler next) {
        return ProxyHandler.builder().setProxyClient(proxyClient).setNext(next).build();
    }

    /**
     * Returns a handler that can act as a load balancing reverse proxy.
     *
     * @param proxyClient The proxy client to use to connect to the remote server
     * @return The proxy handler
     */
    public static ProxyHandler proxyHandler(ProxyClient proxyClient) {
        return ProxyHandler.builder().setProxyClient(proxyClient).build();
    }

    /**
     * Handler that sets the headers that disable caching of the response
     * @param next The next handler
     * @return The handler
     */
    public static HttpHandler disableCache(final HttpHandler next) {
        return new DisableCacheHandler(next);
    }

    /**
     * Returns a handler that dumps requests to the log for debugging purposes.
     *
     * @param next The next handler
     * @return The request dumping handler
     */
    public static HttpHandler requestDump(final HttpHandler next) {
        return new RequestDumpingHandler(next);
    }

    /**
     * Returns a handler that maps exceptions to additional handlers
     * @param next The next handler
     * @return The exception handler
     */
    public static ExceptionHandler exceptionHandler(final HttpHandler next) {
        return new ExceptionHandler(next);
    }

    /**
     *
     * A handler that limits the download speed to a set number of bytes/period
     *
     * @param next The next handler
     * @param bytes The number of bytes per time period
     * @param time The time period
     * @param timeUnit The units of the time period
     */
    public static ResponseRateLimitingHandler responseRateLimitingHandler(HttpHandler next, int bytes,long time, TimeUnit timeUnit) {
        return new ResponseRateLimitingHandler(next, bytes, time, timeUnit);
    }

    /**
     * Creates a handler that automatically learns which resources to push based on the referer header
     *
     * @param maxEntries The maximum number of entries to store
     * @param maxAge The maximum age of the entries
     * @param next The next handler
     * @return A caching push handler
     */
    public static LearningPushHandler learningPushHandler(int maxEntries, int maxAge, HttpHandler next) {
        return new LearningPushHandler(maxEntries, maxAge, next);
    }

    /**
     * A handler that sets response code but continues the exchange so the servlet's
     * error page can be returned.
     *
     * @param responseCode The response code to set
     * @param next The next handler
     * @return A Set Error handler
     */
    public static SetErrorHandler setErrorHandler(int responseCode, HttpHandler next) {
        return new SetErrorHandler(next, responseCode);
    }

    /**
     * Creates a handler that automatically learns which resources to push based on the referer header
     *
     * @param maxEntries The maximum number of entries to store
     * @param next The next handler
     * @return A caching push handler
     */
    public static LearningPushHandler learningPushHandler(int maxEntries, HttpHandler next) {
        return new LearningPushHandler(maxEntries, -1, next);
    }

    private Handlers() {

    }

    public static void handlerNotNull(final HttpHandler handler) {
        if (handler == null) {
            throw UndertowMessages.MESSAGES.handlerCannotBeNull();
        }
    }
}
