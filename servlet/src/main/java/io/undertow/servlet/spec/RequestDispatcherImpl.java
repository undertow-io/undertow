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

package io.undertow.servlet.spec;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.handlers.FilterHandler;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.util.DelegatingHttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class RequestDispatcherImpl implements RequestDispatcher {

    private final ServletInitialHandler handler;

    public RequestDispatcherImpl(final ServletInitialHandler handler) {
        this.handler = handler;
    }

    @Override
    public void forward(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {

    }

    @Override
    public void include(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        final BlockingHttpServerExchange exchange= getExchange(request);

        final ServletRequest oldRequest = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        final ServletResponse oldResponse = exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        exchange.getExchange().putAttachment(FilterHandler.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.INCLUDE);
        try {
            try {
                exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, new ForwardHttpServletResponse((HttpServletResponse) response));
                handler.handleRequest(exchange);
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldRequest);
            exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResponse);
        }
    }

    private BlockingHttpServerExchange getExchange(final ServletRequest request) {
        if(request instanceof HttpServletRequestImpl) {
            return  ((HttpServletRequestImpl) request).getExchange();
        } else if(request instanceof ServletRequestWrapper) {
            return getExchange(((ServletRequestWrapper)request).getRequest());
        } else {
            throw UndertowServletMessages.MESSAGES.requestNoOfCorrectType();
        }
    }

    private final class ForwardHttpServletResponse extends DelegatingHttpServletResponse {

        public ForwardHttpServletResponse(final HttpServletResponse delegate) {
            super(delegate);
        }

        @Override
        public void addCookie(final Cookie cookie) {
        }

        @Override
        public void setDateHeader(final String name, final long date) {
        }

        @Override
        public void addDateHeader(final String name, final long date) {
        }

        @Override
        public void setHeader(final String name, final String value) {
        }

        @Override
        public void addHeader(final String name, final String value) {
        }

        @Override
        public void setIntHeader(final String name, final int value) {
        }

        @Override
        public void addIntHeader(final String name, final int value) {
        }

        @Override
        public void setStatus(final int sc) {
        }

        @Override
        public void setStatus(final int sc, final String sm) {
        }
    }
}
