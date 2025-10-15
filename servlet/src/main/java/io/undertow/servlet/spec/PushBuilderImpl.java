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
import io.undertow.util.Methods;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.PushBuilder;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class PushBuilderImpl implements PushBuilder {

    private static final Set<HttpString> IGNORE;
    private static final Set<HttpString> CONDITIONAL;
    private static final Set<String> INVALID_METHOD;
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
        ignore.add(Headers.REFERER);
        IGNORE = Collections.unmodifiableSet(ignore);

        final Set<HttpString> conditional = new HashSet<>();
        conditional.add(Headers.IF_MATCH);
        conditional.add(Headers.IF_NONE_MATCH);
        conditional.add(Headers.IF_MODIFIED_SINCE);
        conditional.add(Headers.IF_UNMODIFIED_SINCE);
        conditional.add(Headers.IF_RANGE);
        CONDITIONAL = Collections.unmodifiableSet(conditional);
        final Set<String> invalid = new HashSet<>();
        invalid.add(Methods.OPTIONS_STRING);
        invalid.add(Methods.PUT_STRING);
        invalid.add(Methods.POST_STRING);
        invalid.add(Methods.DELETE_STRING);
        invalid.add(Methods.CONNECT_STRING);
        invalid.add(Methods.TRACE_STRING);
        invalid.add("");
        INVALID_METHOD = Collections.unmodifiableSet(invalid);
    }

    private final HttpServletRequestImpl servletRequest;
    private String method;
    private String queryString;
    private String sessionId;
    private final HeaderMap headers = new HeaderMap();
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
        for (Cookie cookie : servletRequest.getExchange().responseCookies()) {
            if(cookie.getMaxAge() != null && cookie.getMaxAge() <= 0) {
                //remove cookie
                HeaderValues existing = headers.get(Headers.COOKIE);
                if(existing != null) {
                    Iterator<String> it = existing.iterator();
                    while (it.hasNext()) {
                        String val = it.next();
                        if(val.startsWith(cookie.getName() + "=")) {
                            it.remove();
                        }
                    }
                }
            } else if(!cookie.getName().equals(servletRequest.getServletContext().getSessionCookieConfig().getName())){
                headers.add(Headers.COOKIE, cookie.getName() + "=" + cookie.getValue());
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
            if (sessionId != null) {
                newHeaders.put(Headers.COOKIE, "JSESSIONID=" + sessionId); //TODO: do this properly, may be a different tracking method or a different cookie name
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
            con.pushResource(path, new HttpString(method), newHeaders);
        }
        path = null;
        for(HttpString h : CONDITIONAL) {
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

}
