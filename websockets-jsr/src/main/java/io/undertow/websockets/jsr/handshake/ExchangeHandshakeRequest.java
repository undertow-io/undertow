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
package io.undertow.websockets.jsr.handshake;

import java.net.URI;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.HandshakeRequest;

import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * {@link HandshakeRequest} which wraps a {@link io.undertow.websockets.spi.WebSocketHttpExchange} to act on it.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class ExchangeHandshakeRequest implements HandshakeRequest {
    private final HttpServletRequest request;
    private Map<String, List<String>> headers;

    public ExchangeHandshakeRequest(HttpServletRequest request) {
        this.request = request;
    }


    @Override
    public Map<String, List<String>> getHeaders() {
        if (headers == null) {
            headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
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
        }
        return headers;
    }

    @Override
    public Principal getUserPrincipal() {
        return request.getUserPrincipal();
    }

    @Override
    public URI getRequestURI() {
        return URI.create(request.getRequestURI());
    }

    @Override
    public boolean isUserInRole(String role) {
        return request.isUserInRole(role);
    }

    @Override
    public Object getHttpSession() {
        return request.getSession();
    }

    @Override
    public Map<String, List<String>> getParameterMap() {
        Map<String, List<String>> requestParameters = new HashMap<>();
        for(Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            List<String> list = requestParameters.get(e.getKey());
            if(list == null) {
                requestParameters.put(e.getKey(), list = new ArrayList<>());
            }
            list.addAll(Arrays.asList(e.getValue()));
        }
        Map<String, String> pathParms = exchange.getAttachment(HandshakeUtil.PATH_PARAMS);
        if(pathParms != null) {
            for(Map.Entry<String, String> e : pathParms.entrySet()) {
                List<String> list = requestParameters.get(e.getKey());
                if(list == null) {
                    requestParameters.put(e.getKey(), list = new ArrayList<>());
                }
                list.add(e.getValue());
            }
        }
        return Collections.unmodifiableMap(requestParameters);
    }

    @Override
    public String getQueryString() {
        return exchange.getQueryString();
    }
}
