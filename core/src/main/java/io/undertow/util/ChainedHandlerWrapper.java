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

package io.undertow.util;

import java.util.List;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;

/**
 * Handler wrapper that chains several handler wrappers together.
 *
 * @author Stuart Douglas
 */
public class ChainedHandlerWrapper implements HandlerWrapper {

    private final List<HandlerWrapper> handlers;

    public ChainedHandlerWrapper(List<HandlerWrapper> handlers) {
        this.handlers = handlers;
    }

    @Override
    public HttpHandler wrap(HttpHandler handler) {
        HttpHandler cur = handler;
        for(HandlerWrapper h : handlers) {
            cur = h.wrap(cur);
        }
        return cur;
    }
}
