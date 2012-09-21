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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.handlers.ServletInitialHandler;

/**
 * @author Stuart Douglas
 */
public class RequestDispatcherImpl implements RequestDispatcher {

    private final ServletInitialHandler handler;
    private final BlockingHttpServerExchange exchange;

    public RequestDispatcherImpl(final ServletInitialHandler handler, final BlockingHttpServerExchange exchange) {
        this.handler = handler;
        this.exchange = exchange;
    }

    @Override
    public void forward(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
            int old
    }

    @Override
    public void include(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {

    }
}
