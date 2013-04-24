/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpHandlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.handlers.ServletAttachments;

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
        final ServletAttachments servletAttachments = exchange.getAttachment(ServletAttachments.ATTACHMENT_KEY);
        List<SingleConstraintMatch> list = servletAttachments.getRequiredConstrains();
        if (list == null) {
            servletAttachments.setRequiredConstrains(list = new ArrayList<>());
        }
        list.addAll(securityMatch.getRequiredConstraints());
        TransportGuaranteeType type = servletAttachments.getTransportGuarenteeType();
        if (type == null || type.ordinal() < securityMatch.getTransportGuaranteeType().ordinal()) {
            servletAttachments.setTransportGuarenteeType(securityMatch.getTransportGuaranteeType());
        }
        HttpHandlers.executeHandler(next, exchange);
    }
}
