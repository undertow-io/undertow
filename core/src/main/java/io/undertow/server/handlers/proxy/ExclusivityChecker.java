package io.undertow.server.handlers.proxy;

import io.undertow.server.HttpServerExchange;

/**
 * Interface that is used to determine if a connection should be exclusive.
 *
 * If a connection is exclusive then it is removed from the connection pool, and a one
 * to one mapping will be maintained between the front and back end servers.
 *
* @author Stuart Douglas
*/
public interface ExclusivityChecker {

    boolean isExclusivityRequired(HttpServerExchange exchange);

}
