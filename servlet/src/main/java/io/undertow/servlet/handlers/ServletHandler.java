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

package io.undertow.servlet.handlers;

import java.io.IOException;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.UnavailableException;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.core.ManagedServlet;
import io.undertow.util.StatusCodes;

/**
 * The handler that is responsible for invoking the servlet
 * <p>
 * TODO: do we want to move lifecycle considerations out of this handler?
 *
 * @author Stuart Douglas
 */
public class ServletHandler implements HttpHandler {

    private final ManagedServlet managedServlet;


    public ServletHandler(final ManagedServlet managedServlet) {
        this.managedServlet = managedServlet;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws IOException, ServletException {
        if (managedServlet.isPermanentlyUnavailable()) {
            UndertowServletLogger.REQUEST_LOGGER.debugf("Returning 404 for servlet %s due to permanent unavailability", managedServlet.getServletInfo().getName());
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            return;
        }

        if (managedServlet.isTemporarilyUnavailable()) {
            UndertowServletLogger.REQUEST_LOGGER.debugf("Returning 503 for servlet %s due to temporary unavailability", managedServlet.getServletInfo().getName());
            exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
            return;
        }
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if(!managedServlet.getServletInfo().isAsyncSupported()) {
            servletRequestContext.setAsyncSupported(false);
        }
        ServletRequest request = servletRequestContext.getServletRequest();
        ServletResponse response = servletRequestContext.getServletResponse();
        InstanceHandle<? extends Servlet> servlet = null;
        try {
            servlet = managedServlet.getServlet();
            servlet.getInstance().service(request, response);

            //according to the spec we have to call AsyncContext.complete() at this point
            //straight after the service method
            //not super sure about this, surely it would make more sense to do this when the request has returned to the container, however the spec is quite clear wording wise
            //todo: should we actually enable this? Apparently other containers do not do it
            //if(!request.isAsyncStarted()) {
            //    AsyncContextImpl existingAsyncContext = servletRequestContext.getOriginalRequest().getAsyncContextInternal();
            //    if (existingAsyncContext != null) {
            //        existingAsyncContext.complete();
            //    }
            //}
        } catch (UnavailableException e) {
            managedServlet.handleUnavailableException(e);
            if (e.isPermanent()) {
                exchange.setStatusCode(StatusCodes.NOT_FOUND);
            } else {
                exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
            }
        } finally {
            if(servlet != null) {
                servlet.release();
            }
        }
    }

    public ManagedServlet getManagedServlet() {
        return managedServlet;
    }
}
