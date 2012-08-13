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

package io.undertow.servlet.handlers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;

/**
 * @author Stuart Douglas
 */
public class ServletInvocationHandler implements BlockingHttpHandler {

    private final Object servlet;
    private final Method service;

    public ServletInvocationHandler(final Object servlet, final Method service) {
        this.servlet = servlet;
        this.service = service;
    }

    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) {
        HttpServletRequestImpl request = (HttpServletRequestImpl) exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        HttpServletResponseImpl response = (HttpServletResponseImpl) exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        try {
            service.invoke(servlet, request, response);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
