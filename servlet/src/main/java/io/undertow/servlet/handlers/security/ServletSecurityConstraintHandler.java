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

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.SingleConstraintMatch;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.handlers.ServletRequestContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class ServletSecurityConstraintHandler implements HttpHandler {

    private final SecurityPathMatches securityPathMatches;
    private final HttpHandler next;

    public ServletSecurityConstraintHandler(final SecurityPathMatches securityPathMatches, final HttpHandler next) {
        this.securityPathMatches = securityPathMatches;
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final String path = exchange.getRelativePath();
        SecurityPathMatch securityMatch = securityPathMatches.getSecurityInfo(path, exchange.getRequestMethod().toString());
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        List<SingleConstraintMatch> list = servletRequestContext.getRequiredConstrains();
        if (list == null) {
            servletRequestContext.setRequiredConstrains(list = new ArrayList<>());
        }
        list.add(securityMatch.getMergedConstraint());
        TransportGuaranteeType type = servletRequestContext.getTransportGuarenteeType();
        if (type == null || type.ordinal() < securityMatch.getTransportGuaranteeType().ordinal()) {
            servletRequestContext.setTransportGuarenteeType(securityMatch.getTransportGuaranteeType());
        }

        UndertowLogger.SECURITY_LOGGER.debugf("Security constraints for request %s are %s", exchange.getRequestURI(), list);
        next.handleRequest(exchange);
    }
}
