/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.websockets.jsr;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.netty.channel.ChannelHandlerContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.AttachmentKey;
import io.undertow.util.IoUtils;
import io.undertow.websockets.jsr.handshake.WebSocketHttpExchange;

/**
 * @author Stuart Douglas
 */
public class ServletWebSocketHttpExchange implements WebSocketHttpExchange {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final HttpServerExchange exchange;

    public ServletWebSocketHttpExchange(final HttpServletRequest request, final HttpServletResponse response) {
        this.request = request;
        this.response = response;
        this.exchange = ServletRequestContext.requireCurrent().getOriginalRequest().getExchange();
    }


    @Override
    public <T> void putAttachment(final AttachmentKey<T> key, final T value) {
        exchange.putAttachment(key, value);
    }

    @Override
    public <T> T getAttachment(final AttachmentKey<T> key) {
        return exchange.getAttachment(key);
    }

    @Override
    public String getRequestHeader(final String headerName) {
        return request.getHeader(headerName);
    }

    @Override
    public Map<String, List<String>> getRequestHeaders() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            final Enumeration<String> theHeaders = request.getHeaders(header);
            final List<String> vals = new ArrayList<>();
            headers.put(header, vals);
            while (theHeaders.hasMoreElements()) {
                vals.add(theHeaders.nextElement());
            }

        }
        return Collections.unmodifiableMap(headers);
    }

    @Override
    public String getResponseHeader(final String headerName) {
        return response.getHeader(headerName);
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        final Collection<String> headerNames = response.getHeaderNames();
        for (String header : headerNames) {
            headers.put(header, new ArrayList<>(response.getHeaders(header)));
        }
        return Collections.unmodifiableMap(headers);
    }

    @Override
    public void setResponseHeaders(final Map<String, List<String>> headers) {
        for (String header : response.getHeaderNames()) {
            response.setHeader(header, null);
        }

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String val : entry.getValue()) {
                response.addHeader(entry.getKey(), val);
            }
        }
    }

    @Override
    public void setResponseHeader(final String headerName, final String headerValue) {
        response.setHeader(headerName, headerValue);
    }

    @Override
    public void upgradeChannel(Consumer<ChannelHandlerContext> listener) {
        exchange.upgradeChannel(listener);
    }

    @Override
    public void endExchange() {
        //noop
    }

    @Override
    public void close() {
        exchange.endExchange();
    }

    @Override
    public String getRequestScheme() {
        return request.getScheme();
    }

    @Override
    public String getRequestURI() {
        return request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
    }

    @Override
    public String getQueryString() {
        return request.getQueryString();
    }

    @Override
    public Object getSession() {
        return request.getSession(false);
    }

    @Override
    public Map<String, List<String>> getRequestParameters() {
        Map<String, List<String>> params = new HashMap<>();
        for (Map.Entry<String, String[]> param : request.getParameterMap().entrySet()) {
            params.put(param.getKey(), new ArrayList<>(Arrays.asList(param.getValue())));
        }
        return params;
    }

    @Override
    public Principal getUserPrincipal() {
        return request.getUserPrincipal();
    }

    @Override
    public boolean isUserInRole(String role) {
        return request.isUserInRole(role);
    }

    @Override
    public Executor getExecutor() {
        return exchange.getConnection().getWorker();
    }
}
