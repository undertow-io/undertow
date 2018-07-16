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

package io.undertow.server.handlers.resource;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

import java.util.Set;
import java.util.StringJoiner;

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
		final StringJoiner stringJoiner = new StringJoiner( ", ");

		for ( HttpString notAllowedMethod : notAllowedMethods ) {
			stringJoiner.add( notAllowedMethod.toString() );
		}

		exchange.setStatusCode( StatusCodes.METHOD_NOT_ALLOWED );
		exchange.getResponseHeaders().add( Headers.ALLOW, stringJoiner.toString() );
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
