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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.HttpHandlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.DefaultServletConfig;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.ETagUtils;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.SameThreadExecutor;

/**
 * Default servlet responsible for serving up resources. This is both a handler and a servlet. If no filters
 * match the current path then the resources will be served up asynchronously using the
 * {@link io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)} method,
 * otherwise the request is handled as a normal servlet request.
 * <p/>
 * By default we only allow a restricted set of extensions.
 * <p/>
 * todo: this thing needs a lot more work. In particular:
 * - caching for blocking requests
 * - correct mime type
 * - directory listings
 * - range/last-modified and other headers to be handled properly
 * - head requests
 * - and probably heaps of other things
 *
 * @author Stuart Douglas
 */
public class DefaultServlet extends HttpServlet {

    private final Deployment deployment;
    private final DefaultServletConfig config;
    private final ResourceManager resourceManager;

    private final List<String> welcomePages;

    public DefaultServlet(final Deployment deployment, final DefaultServletConfig config, final List<String> welcomePages) {
        this.deployment = deployment;
        this.config = config;
        this.welcomePages = welcomePages;
        this.resourceManager = deployment.getDeploymentInfo().getResourceManager();
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final String path = getPath(req);
        if (!isAllowed(path)) {
            resp.sendError(404);
            return;
        }
        final Resource resource = resourceManager.getResource(path);
        if (resource == null) {
            if (req.getDispatcherType() == DispatcherType.INCLUDE) {
                //servlet 9.3
                throw new FileNotFoundException(path);
            } else {
                resp.sendError(404);
            }
            return;
        } else if (resource.isDirectory()) {
            handleWelcomePage(req, resp, path);
        } else {
            serveFileBlocking(req, resp, resource);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        /*
         * Where a servlet has received a POST request we still require the capability to include static content.
         */
        switch (req.getDispatcherType()) {
            case INCLUDE:
            case FORWARD:
                doGet(req, resp);
                break;
            default:
                super.doPost(req, resp);
        }
    }

    private void serveFileBlocking(final HttpServletRequest req, final HttpServletResponse resp, final Resource resource) throws IOException {
        final ETag etag = resource.getETag();
        final Date lastModified = resource.getLastModified();
        if (!ETagUtils.handleIfMatch(req.getHeader(Headers.IF_MATCH_STRING), etag, false) ||
                !DateUtils.handleIfUnmodifiedSince(req.getHeader(Headers.IF_UNMODIFIED_SINCE_STRING), lastModified)) {
            resp.setStatus(412);
            return;
        }
        if (!ETagUtils.handleIfNoneMatch(req.getHeader(Headers.IF_NONE_MATCH_STRING), etag, true) ||
                !DateUtils.handleIfModifiedSince(req.getHeader(Headers.IF_MODIFIED_SINCE_STRING), lastModified)) {
            resp.setStatus(304);
            return;
        }
        //todo: handle range requests
        //we are going to proceed. Set the appropriate headers
        final String contentType = deployment.getServletContext().getMimeType(resource.getName());
        if (contentType != null) {
            resp.setHeader(Headers.CONTENT_TYPE_STRING, contentType);
        } else {
            resp.setHeader(Headers.CONTENT_TYPE_STRING, "application/octet-stream");
        }
        if (lastModified != null) {
            resp.setHeader(Headers.LAST_MODIFIED_STRING, resource.getLastModifiedString());
        }
        if (etag != null) {
            resp.setHeader(Headers.ETAG_STRING, etag.toString());
        }
        Long contentLength = resource.getContentLength();
        if (contentLength != null) {
            resp.setContentLengthLong(contentLength);
        }
        if (!req.getMethod().equals(Methods.HEAD_STRING)) {
            resource.serve(ServletRequestContext.requireCurrent().getOriginalRequest().getExchange());
        }
    }

    private void handleWelcomePage(final HttpServletRequest req, final HttpServletResponse resp, final String oldPath) throws IOException, ServletException {
        String welcomePage = findWelcomeFile(oldPath);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            pathInfo = "/";
        }
        final String pathWithTraingSlash = pathInfo.endsWith("/") ? pathInfo : pathInfo + "/";
        if (welcomePage != null) {
            redirect(req, welcomePage);
        } else {
            String path = findWelcomeServlet(pathWithTraingSlash);
            if (path != null) {
                redirect(req, path);
            } else {
                resp.sendError(404);
            }
        }
    }

    private void redirect(final HttpServletRequest req, final String pathAddition) {
        //we need to redirect in a manner that is indistinguishable from a a direct request
        //we can't just use a forward, as these do not have security applied, and
        //also the filters that have been applied to the request would be different.
        //instead we get the exchange and do a dispatch, and then redirect. This basically acts like
        //two seperate servlet requests
        final HttpServletRequestImpl requestImpl = ServletRequestContext.requireCurrent().getOriginalRequest();
        final HttpServerExchange exchange = requestImpl.getExchange();
        if(!exchange.isRequestChannelAvailable()) {
            throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();
        }
        exchange.dispatch(SameThreadExecutor.INSTANCE, new Runnable() {
            @Override
            public void run() {
                String path = pathAddition;
                if(!exchange.getRelativePath().endsWith("/")) {
                    path = "/" + path;
                }

                exchange.getResponseHeaders().clear();
                exchange.setResponseCode(200);

                exchange.setRelativePath(exchange.getRelativePath() + path);
                exchange.setRequestPath(exchange.getRequestPath() + path);
                exchange.setRequestURI(exchange.getRequestURI() + path);
                HttpHandlers.executeRootHandler(requestImpl.getServletContext().getDeployment().getServletHandler(), exchange, false);
            }
        });

    }

    private String findWelcomeFile(final String path) {
        String realPath = path.endsWith("/") ? path : path + "/";
        for (String i : welcomePages) {
            try {
                Resource resource = resourceManager.getResource(realPath + i);
                if (resource != null) {
                    return i;
                }
            } catch (IOException e) {
            }
        }
        return null;
    }

    private String findWelcomeServlet(final String path) {
        for (String i : welcomePages) {
            final ServletPathMatch handler = deployment.getServletPaths().getServletHandlerByExactPath(path + i);
            if (handler != null) {
                return i;
            }
        }
        return null;
    }

    private String getPath(final HttpServletRequest request) {
        if (request.getDispatcherType() == DispatcherType.INCLUDE && request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null) {
            String result = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            if (result == null) {
                result = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
            }
            if (result == null || result.equals("")) {
                result = "/";
            }
            return result;
        } else {
            String result = request.getPathInfo();
            if (result == null) {
                result = request.getServletPath();
            }
            if ((result == null) || (result.equals(""))) {
                result = "/";
            }
            return result;
        }
    }

    private boolean isAllowed(String path) {
        if (!path.isEmpty()) {
            if (path.startsWith("/META-INF") ||
                    path.startsWith("META-INF") ||
                    path.startsWith("/WEB-INF") ||
                    path.startsWith("WEB-INF")) {
                return false;
            }
        }
        int pos = path.lastIndexOf('/');
        final String lastSegment;
        if (pos == -1) {
            lastSegment = path;
        } else {
            lastSegment = path.substring(pos + 1);
        }
        if (lastSegment.isEmpty()) {
            return true;
        }
        int ext = lastSegment.lastIndexOf('.');
        if (ext == -1) {
            //no extension
            return true;
        }
        final String extension = lastSegment.substring(ext + 1, lastSegment.length());
        if (config.isDefaultAllowed()) {
            return !config.getDisallowed().contains(extension);
        } else {
            return config.getAllowed().contains(extension);
        }
    }

}
