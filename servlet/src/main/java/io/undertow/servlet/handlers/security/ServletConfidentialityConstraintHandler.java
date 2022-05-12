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
package io.undertow.servlet.handlers.security;

import static io.undertow.servlet.UndertowServletMessages.MESSAGES;

import io.undertow.security.handlers.SinglePortConfidentialityHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.AuthorizationManager;
import io.undertow.servlet.api.ConfidentialPortManager;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.StatusCodes;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Servlet specific extension to {@link SinglePortConfidentialityHandler}
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ServletConfidentialityConstraintHandler extends SinglePortConfidentialityHandler {

    private final ConfidentialPortManager portManager;

    public ServletConfidentialityConstraintHandler(final ConfidentialPortManager portManager, final HttpHandler next) {
        super(next, -1);
        this.portManager = portManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        final AuthorizationManager authorizationManager = servletRequestContext.getDeployment().getDeploymentInfo().getAuthorizationManager();

        TransportGuaranteeType connectionGuarantee = servletRequestContext.getOriginalRequest().isSecure() ? TransportGuaranteeType.CONFIDENTIAL : TransportGuaranteeType.NONE;
        TransportGuaranteeType transportGuarantee = authorizationManager.transportGuarantee(connectionGuarantee,
                servletRequestContext.getTransportGuarenteeType(), servletRequestContext.getOriginalRequest());
        servletRequestContext.setTransportGuarenteeType(transportGuarantee);

        if (TransportGuaranteeType.REJECTED == transportGuarantee) {
            HttpServletResponse response = (HttpServletResponse) servletRequestContext.getServletResponse();
            response.sendError(StatusCodes.FORBIDDEN);
            return;
        }
        super.handleRequest(exchange);
    }

    @Override
    protected boolean confidentialityRequired(HttpServerExchange exchange) {
        TransportGuaranteeType transportGuarantee = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getTransportGuarenteeType();

        // TODO - We may be able to add more flexibility here especially with authentication mechanisms such as Digest for
        // INTEGRAL - for now just use SSL.
        return (TransportGuaranteeType.CONFIDENTIAL == transportGuarantee || TransportGuaranteeType.INTEGRAL == transportGuarantee);
    }

    @Override
    protected URI getRedirectURI(HttpServerExchange exchange) throws URISyntaxException {
        int port = portManager.getConfidentialPort(exchange);
        if (port < 0) {
            throw MESSAGES.noConfidentialPortAvailable();
        }

        return super.getRedirectURI(exchange, port);
    }

    /**
     * Use the HttpServerExchange supplied to check if this request is already 'sufficiently' confidential.
     *
     * Here we say 'sufficiently' as sub-classes can override this and maybe even go so far as querying the actual SSLSession.
     *
     * @param exchange - The {@link HttpServerExchange} for the request being processed.
     * @return true if the request is 'sufficiently' confidential, false otherwise.
     */
    protected boolean isConfidential(final HttpServerExchange exchange) {
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if(src != null) {
            return src.getOriginalRequest().isSecure();
        }
        return super.isConfidential(exchange);
    }
}
