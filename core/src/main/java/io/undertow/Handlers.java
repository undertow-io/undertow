package io.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.core.handler.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.impl.WebSocketSessionConnectionCallback;

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
    public static WebSocketProtocolHandshakeHandler websocket(final WebSocketSessionHandler sessionHandler) {
        return new WebSocketProtocolHandshakeHandler(new WebSocketSessionConnectionCallback(sessionHandler));
    }

    /**
     * @param sessionHandler The web socket session handler
     * @param next           The handler to invoke if the web socket connection fails
     * @return The web socket handler
     */
    public static WebSocketProtocolHandshakeHandler websocket(final WebSocketSessionHandler sessionHandler, final HttpHandler next) {
        return new WebSocketProtocolHandshakeHandler(new WebSocketSessionConnectionCallback(sessionHandler), next);
    }

    /**
     * Return a new resource handler
     * @param resourceManager The resource manager to use
     * @return A new resource handler
     */
    public static ResourceHandler resource(final ResourceManager resourceManager) {
        return new ResourceHandler().setResourceManager(resourceManager).setDirectoryListingEnabled(false);
    }

    /**
     * Returns a new redirect handler
     * @param location The redirect location
     * @return A new redirect handler
     */
    public static RedirectHandler redirect(final String location) {
        return new RedirectHandler(location);
    }

    private Handlers() {

    }

}
