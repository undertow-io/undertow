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

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;
import javax.servlet.http.PushBuilder;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.HttpMethodNames;

/**
 * @author Stuart Douglas
 */
public class PushBuilderImpl implements PushBuilder {

    private static final Set<String> IGNORE;
    private static final Set<String> CONDITIONAL;
    private static final Set<String> INVALID_METHOD;
    static {
        final Set<String> ignore = new HashSet<>();
        ignore.add(HttpHeaderNames.IF_MATCH);
        ignore.add(HttpHeaderNames.IF_NONE_MATCH);
        ignore.add(HttpHeaderNames.IF_MODIFIED_SINCE);
        ignore.add(HttpHeaderNames.IF_UNMODIFIED_SINCE);
        ignore.add(HttpHeaderNames.IF_RANGE);
        ignore.add(HttpHeaderNames.RANGE);
        ignore.add(HttpHeaderNames.ACCEPT_RANGES);
        ignore.add(HttpHeaderNames.EXPECT);
        ignore.add(HttpHeaderNames.REFERER);
        IGNORE = Collections.unmodifiableSet(ignore);

        final Set<String> conditional = new HashSet<>();
        conditional.add(HttpHeaderNames.IF_MATCH);
        conditional.add(HttpHeaderNames.IF_NONE_MATCH);
        conditional.add(HttpHeaderNames.IF_MODIFIED_SINCE);
        conditional.add(HttpHeaderNames.IF_UNMODIFIED_SINCE);
        conditional.add(HttpHeaderNames.IF_RANGE);
        CONDITIONAL = Collections.unmodifiableSet(conditional);
        final Set<String> invalid = new HashSet<>();
        invalid.add(HttpMethodNames.OPTIONS);
        invalid.add(HttpMethodNames.PUT);
        invalid.add(HttpMethodNames.POST);
        invalid.add(HttpMethodNames.DELETE);
        invalid.add(HttpMethodNames.CONNECT);
        invalid.add(HttpMethodNames.TRACE);
        invalid.add("");
        INVALID_METHOD = Collections.unmodifiableSet(invalid);
    }

    private final HttpServletRequestImpl servletRequest;
    private String method;
    private String queryString;
    private String sessionId;
    private final HttpHeaders headers = new DefaultHttpHeaders();
    private String path;

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

        HttpHeaders headers = servletRequest.getExchange().requestHeaders();
        for(Map.Entry<String, String> header : headers) {
            if(!IGNORE.contains(header.getKey())) {
                this.headers.add(header.getKey(), headers.getAll(header.getKey()));
            }
        }
        if(servletRequest.getQueryString() == null) {
            this.headers.add(HttpHeaderNames.REFERER, servletRequest.getRequestURL().toString());
        } else {
            this.headers.add(HttpHeaderNames.REFERER, servletRequest.getRequestURL()  + "?" + servletRequest.getQueryString());
        }
        this.path = null;
        for(Map.Entry<String, Cookie> cookie : servletRequest.getExchange().getResponseCookies().entrySet()) {
            if(cookie.getValue().getMaxAge() != null && cookie.getValue().getMaxAge() <= 0) {
                //remove cookie
                List<String> existing = this.headers.getAll(HttpHeaderNames.COOKIE);
                if(existing != null) {
                    Iterator<String> it = existing.iterator();
                    while (it.hasNext()) {
                        String val = it.next();
                        if(val.startsWith(cookie.getKey() + "=")) {
                            it.remove();
                        }
                    }
                }
            } else if(!cookie.getKey().equals(servletRequest.getServletContext().getSessionCookieConfig().getName())){
                this.headers.add(HttpHeaderNames.COOKIE, cookie.getKey() + "=" + cookie.getValue().getValue());
            }
        }

    }


    @Override
    public PushBuilder method(String method) {
        if(method == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNullNPE("method");
        }
        if(INVALID_METHOD.contains(method)) {
            throw UndertowServletMessages.MESSAGES.invalidMethodForPushRequest(method);
        }
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
    public PushBuilder setHeader(String name, String value) {
        headers.set(name, value);
        return this;
    }

    @Override
    public PushBuilder addHeader(String name, String value) {
        headers.add(name, value);
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
    public void push() {
        if(path == null) {
            throw UndertowServletMessages.MESSAGES.pathWasNotSet();
        }
        ServerConnection con = servletRequest.getExchange().getConnection();
        if (con.isPushSupported()) {
            HttpHeaders newHeaders = new DefaultHttpHeaders();
            for (Map.Entry<String, String> entry : headers) {
                newHeaders.add(entry.getKey(), headers.getAll(entry.getKey()));
            }
            if (sessionId != null) {
                newHeaders.set(HttpHeaderNames.COOKIE, "JSESSIONID=" + sessionId); //TODO: do this properly, may be a different tracking method or a different cookie name
            }
            String path = this.path;
            if(!path.startsWith("/")) {
                path = servletRequest.getContextPath() + "/" + path;
            }
            if (queryString != null && !queryString.isEmpty()) {
                if(path.contains("?")) {
                    path += "&" + queryString;
                } else {
                    path += "?" + queryString;
                }
            }
            con.pushResource(path, method, newHeaders);
        }
        path = null;
        for(String h : CONDITIONAL) {
            headers.remove(h);
        }
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
    public Set<String> getHeaderNames() {
        Set<String> names = new HashSet<>();
        for(Map.Entry<String, String> name : headers) {
            names.add(name.getKey());
        }
        return names;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public String getPath() {
        return path;
    }

}
