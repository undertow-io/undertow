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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.core.ManagedFilter;
import io.undertow.servlet.spec.AsyncContextImpl;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;

/**
 * @author Stuart Douglas
 */
public class FilterHandler implements BlockingHttpHandler {

    private final Map<DispatcherType, List<ManagedFilter>> filters;
    private final Map<DispatcherType, Boolean> asyncSupported;

    private final BlockingHttpHandler next;

    public FilterHandler(final Map<DispatcherType, List<ManagedFilter>> filters, final BlockingHttpHandler next) {
        this.next = next;
        this.filters = new HashMap<DispatcherType, List<ManagedFilter>>(filters);
        Map<DispatcherType, Boolean> asyncSupported = new HashMap<DispatcherType, Boolean>();
        for(Map.Entry<DispatcherType, List<ManagedFilter>> entry : filters.entrySet()) {
            boolean supported = true;
            for(ManagedFilter i : entry.getValue()) {
                if(!i.getFilterInfo().isAsyncSupported()) {
                    supported = false;
                    break;
                }
            }
            asyncSupported.put(entry.getKey(), supported);
        }
        this.asyncSupported = asyncSupported;
    }

    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
        ServletRequest request = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        ServletResponse response = exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        DispatcherType dispatcher = exchange.getExchange().getAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY);
        exchange.getExchange().putAttachment(AsyncContextImpl.ASYNC_SUPPORTED, asyncSupported.get(dispatcher));

        final List<ManagedFilter> filters = this.filters.get(dispatcher);
        if(filters == null) {
            next.handleRequest(exchange);
        } else {
            final FilterChainImpl filterChain = new FilterChainImpl(exchange, filters, next);
            filterChain.doFilter(request, response);
        }
    }

    private static class FilterChainImpl implements FilterChain {

        int location = 0;
        final BlockingHttpServerExchange exchange;
        final List<ManagedFilter> filters;
        final BlockingHttpHandler next;

        private FilterChainImpl(final BlockingHttpServerExchange exchange, final List<ManagedFilter> filters, final BlockingHttpHandler next) {
            this.exchange = exchange;
            this.filters = filters;
            this.next = next;
        }

        @Override
        public void doFilter(final ServletRequest request, final ServletResponse response) throws IOException, ServletException {

            final ServletRequest oldReq = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
            final ServletResponse oldResp = exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
            try {
                exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                int index = location++;
                if (index >= filters.size()) {
                    next.handleRequest(exchange);
                } else {
                    filters.get(index).doFilter(request, response, this);
                }
            } catch (IOException e) {
                throw e;
            } catch (ServletException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                location--;
                exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldReq);
                exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResp);
            }
        }
    }
}
