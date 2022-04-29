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

package io.undertow.servlet.compat.rewrite;

import io.undertow.server.handlers.resource.Resource;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.DateUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Calendar;

/**
 * @author Remy Maucherat
 */
public class UndertowResolver extends Resolver {

    private final ServletRequestContext servletRequestContext;
    private final HttpServletRequest request;

    public UndertowResolver(ServletRequestContext servletRequestContext, HttpServletRequest request) {
        this.servletRequestContext = servletRequestContext;
        this.request = request;
    }


    /**
     * The following are not implemented:
     * - SERVER_ADMIN
     * - API_VERSION
     * - IS_SUBREQ
     */
    public String resolve(String key) {
        if (key.equals("HTTP_USER_AGENT")) {
            return request.getHeader("user-agent");
        } else if (key.equals("HTTP_REFERER")) {
            return request.getHeader("referer");
        } else if (key.equals("HTTP_COOKIE")) {
            return request.getHeader("cookie");
        } else if (key.equals("HTTP_FORWARDED")) {
            return request.getHeader("forwarded");
        } else if (key.equals("HTTP_HOST")) {
            String host = request.getHeader("host");
            int index = (host != null) ? host.indexOf(':') : -1;
            if (index != -1)
                host = host.substring(0, index);
            return host;
        } else if (key.equals("HTTP_PROXY_CONNECTION")) {
            return request.getHeader("proxy-connection");
        } else if (key.equals("HTTP_ACCEPT")) {
            return request.getHeader("accept");
        } else if (key.equals("REMOTE_ADDR")) {
            return request.getRemoteAddr();
        } else if (key.equals("REMOTE_HOST")) {
            return request.getRemoteHost();
        } else if (key.equals("REMOTE_PORT")) {
            return String.valueOf(request.getRemotePort());
        } else if (key.equals("REMOTE_USER")) {
            return request.getRemoteUser();
        } else if (key.equals("REMOTE_IDENT")) {
            return request.getRemoteUser();
        } else if (key.equals("REQUEST_METHOD")) {
            return request.getMethod();
        } else if (key.equals("SCRIPT_FILENAME")) {
            return request.getServletContext().getRealPath(request.getServletPath());
        } else if (key.equals("REQUEST_PATH")) {
            return servletRequestContext.getExchange().getRelativePath();
        } else if (key.equals("CONTEXT_PATH")) {
            return request.getContextPath();
        } else if (key.equals("SERVLET_PATH")) {
            return emptyStringIfNull(request.getServletPath());
        } else if (key.equals("PATH_INFO")) {
            return emptyStringIfNull(request.getPathInfo());
        } else if (key.equals("QUERY_STRING")) {
            return emptyStringIfNull(request.getQueryString());
        } else if (key.equals("AUTH_TYPE")) {
            return request.getAuthType();
        } else if (key.equals("DOCUMENT_ROOT")) {
            return request.getServletContext().getRealPath("/");
        } else if (key.equals("SERVER_NAME")) {
            return request.getLocalName();
        } else if (key.equals("SERVER_ADDR")) {
            return request.getLocalAddr();
        } else if (key.equals("SERVER_PORT")) {
            return String.valueOf(request.getLocalPort());
        } else if (key.equals("SERVER_PROTOCOL")) {
            return request.getProtocol();
        } else if (key.equals("SERVER_SOFTWARE")) {
            return "tomcat";
        } else if (key.equals("THE_REQUEST")) {
            return request.getMethod() + " " + request.getRequestURI()
                    + " " + request.getProtocol();
        } else if (key.equals("REQUEST_URI")) {
            return request.getRequestURI();
        } else if (key.equals("REQUEST_FILENAME")) {
            return request.getPathTranslated();
        } else if (key.equals("HTTPS")) {
            return request.isSecure() ? "on" : "off";
        } else if (key.equals("TIME_YEAR")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        } else if (key.equals("TIME_MON")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.MONTH));
        } else if (key.equals("TIME_DAY")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        } else if (key.equals("TIME_HOUR")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        } else if (key.equals("TIME_MIN")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.MINUTE));
        } else if (key.equals("TIME_SEC")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.SECOND));
        } else if (key.equals("TIME_WDAY")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        } else if (key.equals("TIME")) {
            return DateUtils.getCurrentDateTime(servletRequestContext.getExchange());
        }
        return null;
    }

    public String resolveEnv(String key) {
        Object result = request.getAttribute(key);
        return (result != null) ? result.toString() : System.getProperty(key);
    }

    public String resolveSsl(String key) {
        // FIXME: Implement SSL environment variables
        return null;
    }

    public String resolveHttp(String key) {
        return request.getHeader(key);
    }

    public boolean resolveResource(int type, String name) {
        Resource resource;
        try {
            resource = servletRequestContext.getDeployment().getDeploymentInfo().getResourceManager().getResource(name);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        switch (type) {
            case 0:
                return (resource == null);
            case 1:
                return (resource != null);
            case 2:
                return (resource != null
                        && resource.getContentLength() > 0);
            default:
                return false;
        }

    }

    private static String emptyStringIfNull(String value) {
        if (value == null) {
            return "";
        } else {
            return value;
        }
    }

}
