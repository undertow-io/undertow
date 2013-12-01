/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.servlet.handlers.security;

import io.undertow.security.handlers.SinglePortConfidentialityHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.AuthorizationManager;
import io.undertow.servlet.api.ConfidentialPortManager;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.handlers.ServletRequestContext;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Servlet specific extension to {@see SinglePortConfidentialityHandler}
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
    protected boolean confidentialityRequired(HttpServerExchange exchange) {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);

        //the configure (via web.xml or annotations) guarantee
        TransportGuaranteeType configuredGuarantee = servletRequestContext.getTransportGuarenteeType();
        Deployment deployment = servletRequestContext.getDeployment();
        final AuthorizationManager authorizationManager = deployment.getDeploymentInfo().getAuthorizationManager();

        TransportGuaranteeType connectionGuarantee = servletRequestContext.getOriginalRequest().isSecure() ? TransportGuaranteeType.CONFIDENTIAL : TransportGuaranteeType.NONE;

        TransportGuaranteeType transportGuarantee = authorizationManager.transportGuarantee(connectionGuarantee, configuredGuarantee, servletRequestContext.getOriginalRequest());

        // TODO - We may be able to add more flexibility here especially with authentication mechanisms such as Digest for
        // INTEGRAL - for now just use SSL.
        return (TransportGuaranteeType.CONFIDENTIAL == transportGuarantee || TransportGuaranteeType.INTEGRAL == transportGuarantee);
    }

    @Override
    protected URI getRedirectURI(HttpServerExchange exchange) throws URISyntaxException {
        return super.getRedirectURI(exchange, portManager.getConfidentialPort(exchange));
    }

}
