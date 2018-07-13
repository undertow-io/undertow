package io.undertow.server.handlers.resource;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

import java.util.Set;

public class UnsupportedMethodHandler implements HttpHandler {
	private final HttpHandler next;
	private final Set<HttpString> notAllowedMethods;
	private final boolean preferNext;

	/**
	 * @param next A fallback handler that will only be used when 'preferNext' is true.
	 * @param preferNext Whether to use the next handler or not.
	 * @param notAllowedMethods A set of http methods which are not allowed when requesting a resource.
	 *
	 * @throws IllegalArgumentException When 'preferNext' is true, and 'next' is null.
	 */
	public UnsupportedMethodHandler( HttpHandler next, boolean preferNext, Set<HttpString> notAllowedMethods ) {
		if ( preferNext && next == null ) {
			throw UndertowMessages.MESSAGES.argumentCannotBeNull("next");
		}

		this.next = next;
		this.notAllowedMethods = notAllowedMethods;
		this.preferNext = preferNext;
	}

	/**
	 * A quick constructor that will always use the next handler.
	 */
	public UnsupportedMethodHandler( HttpHandler next ) {
		this( next, true, null );
	}

	// Maintain backward compatibility for ResourceHandler (this is package-private on purpose)
	UnsupportedMethodHandler( final Set<HttpString> notAllowedMethods ) {
		this.notAllowedMethods = notAllowedMethods;
		this.preferNext = false;
		this.next = null;
	}

	protected void notAllowed( HttpServerExchange exchange ) {
		exchange.setStatusCode( StatusCodes.METHOD_NOT_ALLOWED );
		exchange.getResponseHeaders().add( Headers.ALLOW, String.join( ", ", Methods.GET_STRING, Methods.HEAD_STRING, Methods.POST_STRING ) );
	}

	protected void notImplemented( HttpServerExchange exchange ) {
		exchange.setStatusCode( StatusCodes.NOT_IMPLEMENTED );
	}

	@Override
	public void handleRequest( HttpServerExchange exchange ) throws Exception {
		if ( preferNext && next != null ) {
			next.handleRequest( exchange );
		}
		else if ( notAllowedMethods != null && notAllowedMethods.contains( exchange.getRequestMethod() ) ) {
			notAllowed( exchange );
		}
		else {
			notImplemented( exchange );
		}
	}
}
