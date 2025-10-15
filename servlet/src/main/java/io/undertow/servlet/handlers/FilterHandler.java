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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.core.ManagedFilter;

/**
 * @author Stuart Douglas
 */
public class FilterHandler implements HttpHandler {

    private final Map<DispatcherType, List<ManagedFilter>> filters;
    private final Map<DispatcherType, Boolean> asyncSupported;
    private final boolean allowNonStandardWrappers;

    private final HttpHandler next;

    public FilterHandler(final Map<DispatcherType, List<ManagedFilter>> filters, final boolean allowNonStandardWrappers, final HttpHandler next) {
        this.allowNonStandardWrappers = allowNonStandardWrappers;
        this.next = next;
        this.filters = new EnumMap<>(filters);
        Map<DispatcherType, Boolean> asyncSupported = new EnumMap<>(DispatcherType.class);
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
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        ServletRequest request = servletRequestContext.getServletRequest();
        ServletResponse response = servletRequestContext.getServletResponse();
        DispatcherType dispatcher = servletRequestContext.getDispatcherType();
        Boolean supported = asyncSupported.get(dispatcher);
        if(supported != null && ! supported) {
            servletRequestContext.setAsyncSupported(false);
        }

        final List<ManagedFilter> filters = this.filters.get(dispatcher);
        if(filters == null) {
            next.handleRequest(exchange);
        } else {
            final FilterChainImpl filterChain = new FilterChainImpl(exchange, filters, next, allowNonStandardWrappers);
            filterChain.doFilter(request, response);
        }
    }

    private static class FilterChainImpl implements FilterChain {

        int location = 0;
        final HttpServerExchange exchange;
        final List<ManagedFilter> filters;
        final HttpHandler next;
        final boolean allowNonStandardWrappers;

        private FilterChainImpl(final HttpServerExchange exchange, final List<ManagedFilter> filters, final HttpHandler next, final boolean allowNonStandardWrappers) {
            this.exchange = exchange;
            this.filters = filters;
            this.next = next;
            this.allowNonStandardWrappers = allowNonStandardWrappers;
        }

        @Override
        public void doFilter(final ServletRequest request, final ServletResponse response) throws IOException, ServletException {



            final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
            final ServletRequest oldReq = servletRequestContext.getServletRequest();
            final ServletResponse oldResp = servletRequestContext.getServletResponse();
            try {

                if(!allowNonStandardWrappers) {
                    if(oldReq != request) {
                        if(!(request instanceof ServletRequestWrapper)) {
                            throw UndertowServletMessages.MESSAGES.requestWasNotOriginalOrWrapper(request);
                        }
                    }
                    if(oldResp != response) {
                        if(!(response instanceof ServletResponseWrapper)) {
                            throw UndertowServletMessages.MESSAGES.responseWasNotOriginalOrWrapper(response);
                        }
                    }
                }
                servletRequestContext.setServletRequest(request);
                servletRequestContext.setServletResponse(response);
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
                servletRequestContext.setServletRequest(oldReq);
                servletRequestContext.setServletResponse(oldResp);
            }
        }
    }
}
