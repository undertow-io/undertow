package io.undertow.server.handlers;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

/**
 * A handler that performs reverse DNS lookup to resolve a peer address
 *
 * @author Stuart Douglas
 */
public class PeerNameResolvingHandler implements HttpHandler {

    private final HttpHandler next;
    private final ResolveType resolveType;

    public PeerNameResolvingHandler(HttpHandler next) {
        this.next = next;
        this.resolveType = ResolveType.FORWARD_AND_REVERSE;
    }

    public PeerNameResolvingHandler(HttpHandler next, ResolveType resolveType) {
        this.next = next;
        this.resolveType = resolveType;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final InetSocketAddress address = exchange.getSourceAddress();
        if (address != null) {
            if ((resolveType == ResolveType.FORWARD || resolveType == ResolveType.FORWARD_AND_REVERSE)
                    && address.isUnresolved()) {
                try {
                    if (System.getSecurityManager() == null) {
                        final InetSocketAddress resolvedAddress = new InetSocketAddress(InetAddress.getByName(address.getHostName()), address.getPort());
                        exchange.setSourceAddress(resolvedAddress);
                    } else {
                        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                            @Override
                            public Object run() throws UnknownHostException {
                                final InetSocketAddress resolvedAddress = new InetSocketAddress(InetAddress.getByName(address.getHostName()), address.getPort());
                                exchange.setSourceAddress(resolvedAddress);
                                return null;
                            }
                        });
                    }
                } catch (UnknownHostException e) {
                    UndertowLogger.REQUEST_LOGGER.debugf(e, "Could not resolve hostname %s", address.getHostString());
                }

            } else if (resolveType == ResolveType.REVERSE || resolveType == ResolveType.FORWARD_AND_REVERSE) {
                if (System.getSecurityManager() == null) {
                    address.getHostName();
                } else {
                    AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            address.getHostName();
                            return null;
                        }
                    });
                }
                //we call set source address because otherwise the underlying channel could just return a new address
                exchange.setSourceAddress(address);
            }
        }

        next.handleRequest(exchange);
    }

    public static enum ResolveType {
        FORWARD,
        REVERSE,
        FORWARD_AND_REVERSE;

    }
}
