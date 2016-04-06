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

package io.undertow.servlet.spec;

import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import javax.servlet.http.HttpSession;
import javax.servlet.http.PushBuilder;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class PushBuilderImpl implements PushBuilder {

    private static final Set<HttpString> IGNORE;
    static {
        final Set<HttpString> ignore = new HashSet<>();
        ignore.add(Headers.IF_MATCH);
        ignore.add(Headers.IF_NONE_MATCH);
        ignore.add(Headers.IF_MODIFIED_SINCE);
        ignore.add(Headers.IF_UNMODIFIED_SINCE);
        ignore.add(Headers.IF_RANGE);
        ignore.add(Headers.RANGE);
        ignore.add(Headers.ACCEPT_RANGES);
        ignore.add(Headers.EXPECT);
        ignore.add(Headers.AUTHORIZATION);
        ignore.add(Headers.REFERER);
        IGNORE = Collections.unmodifiableSet(ignore);
    }

    private final HttpServletRequestImpl servletRequest;
    private String method;
    private String queryString;
    private String sessionId;
    private boolean conditional;
    private final HeaderMap headers = new HeaderMap();
    private String path;
    private String etag;
    private String lastModified;

    public PushBuilderImpl(HttpServletRequestImpl servletRequest) {
        //TODO: auth
        this.servletRequest = servletRequest;
        this.method = "GET";
        this.queryString = servletRequest.getQueryString();
        HttpSession session = servletRequest.getSession(false);
        if(session != null) {
            this.sessionId = session.getId();
        } else {
            this.sessionId = servletRequest.getRequestedSessionId();
        }

        this.conditional = servletRequest.getHeader(Headers.IF_NONE_MATCH_STRING) != null || servletRequest.getHeader(Headers.IF_MODIFIED_SINCE_STRING) != null;
        for(HeaderValues header : servletRequest.getExchange().getRequestHeaders()) {
            if(!IGNORE.contains(header.getHeaderName())) {
                headers.addAll(header.getHeaderName(), header);
            }
        }
        if(servletRequest.getQueryString() == null) {
            this.headers.add(Headers.REFERER, servletRequest.getRequestURL().toString());
        } else {
            this.headers.add(Headers.REFERER, servletRequest.getRequestURL()  + "?" + servletRequest.getQueryString());
        }
        this.path = null;
        this.etag = servletRequest.getHeader(Headers.ETAG_STRING);
        for(Map.Entry<String, Cookie> cookie : servletRequest.getExchange().getResponseCookies().entrySet()) {
            if(Objects.equals(0, cookie.getValue().getMaxAge())) {
                //remove cookie
                HeaderValues existing = headers.get(Headers.COOKIE);
                if(existing != null) {
                    Iterator<String> it = existing.iterator();
                    while (it.hasNext()) {
                        String val = it.next();
                        if(val.startsWith(cookie.getKey() + "=")) {
                            it.remove();
                        }
                    }
                }
            } else {
                headers.add(Headers.COOKIE, cookie.getKey() + "=" + cookie.getValue());
            }
        }
        this.lastModified = null;
        this.etag = null;

    }


    @Override
    public PushBuilder method(String method) {
        this.method = method;
        return this;
    }

    @Override
    public PushBuilder queryString(String queryString) {
        this.queryString = queryString;
        return this;
    }

    @Override
    public PushBuilder sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    @Override
    public PushBuilder conditional(boolean conditional) {
        this.conditional = conditional;
        return this;
    }

    @Override
    public PushBuilder setHeader(String name, String value) {
        headers.put(new HttpString(name), value);
        return this;
    }

    @Override
    public PushBuilder addHeader(String name, String value) {
        headers.add(new HttpString(name), value);
        return this;
    }

    @Override
    public PushBuilder removeHeader(String name) {
        headers.remove(name);
        return this;
    }

    @Override
    public PushBuilder path(String path) {
        this.path = path;
        return this;
    }

    @Override
    public PushBuilder etag(String etag) {
        this.etag = etag;
        return this;
    }

    @Override
    public PushBuilder lastModified(String lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    @Override
    public void push() {
        if(path == null) {
            throw UndertowServletMessages.MESSAGES.pathWasNotSet();
        }
        ServerConnection con = servletRequest.getExchange().getConnection();
        if (con.isPushSupported()) {
            HeaderMap newHeaders = new HeaderMap();
            for (HeaderValues entry : headers) {
                newHeaders.addAll(entry.getHeaderName(), entry);
            }
            if (conditional) {
                if (etag != null) {
                    newHeaders.put(Headers.IF_NONE_MATCH, etag);
                } else if (lastModified != null) {
                    newHeaders.put(Headers.IF_MODIFIED_SINCE, lastModified);
                }
            }
            if (sessionId != null) {
                newHeaders.put(Headers.COOKIE, "JSESSIONID=" + sessionId); //TODO: do this properly, may be a different tracking method or a different cookie name
            }
            String path = this.path;
            if (queryString != null && !queryString.isEmpty()) {
                path += "?" + queryString;
            }
            con.pushResource(path, new HttpString(method), newHeaders);
        }
        path = null;
        etag = null;
        lastModified = null;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public boolean isConditional() {
        return conditional;
    }

    @Override
    public Set<String> getHeaderNames() {
        Set<String> names = new HashSet<>();
        for(HeaderValues name : headers) {
            names.add(name.getHeaderName().toString());
        }
        return names;
    }

    @Override
    public String getHeader(String name) {
        return headers.getFirst(name);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getEtag() {
        return etag;
    }

    @Override
    public String getLastModified() {
        return lastModified;
    }
}
