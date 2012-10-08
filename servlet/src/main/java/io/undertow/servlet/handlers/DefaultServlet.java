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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.file.DirectFileCache;
import io.undertow.server.handlers.file.FileCache;
import io.undertow.servlet.api.Deployment;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;
import org.xnio.IoUtils;

/**
 * Default servlet responsible for serving up resources. This is both a handler and a servlet. If no filters
 * match the current path then the resources will be served up asynchronously using the
 * {@link #handleRequest(io.undertow.server.HttpServerExchange, io.undertow.server.HttpCompletionHandler)} method,
 * otherwise the request is handled as a normal servlet request.
 * <p/>
 * By default we only allow a restricted set of extensions.
 *
 * @author Stuart Douglas
 */
public class DefaultServlet extends HttpServlet implements HttpHandler {

    private static final String[] DEFAULT_ALLOWED_EXTENSIONS = {"js", "css", "png", "jpg", "gif", "html", "htm"};
    private static final String[] DEFAULT_DISALLOWED_EXTENSIONS = {"class", "jar", "war", "zip", "xml"};

    private final Deployment deployment;
    private volatile FileCache fileCache = DirectFileCache.INSTANCE;

    private volatile boolean defaultAllowed = false;

    private final Set<String> allowed = Collections.newSetFromMap(new CopyOnWriteMap<String, Boolean>());
    private final Set<String> disallowed = Collections.newSetFromMap(new CopyOnWriteMap<String, Boolean>());

    private final List<String> welcomePages;

    public DefaultServlet(final Deployment deployment, final List<String> welcomePages) {
        this.deployment = deployment;
        this.welcomePages = welcomePages;
        allowed.addAll(Arrays.asList(DEFAULT_ALLOWED_EXTENSIONS));
        disallowed.addAll(Arrays.asList(DEFAULT_DISALLOWED_EXTENSIONS));
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final String path = getPath(req);
        if (!isAllowed(path)) {
            resp.setStatus(404);
            return;
        }
        ServletOutputStream out = null;
        final File resource = deployment.getDeploymentInfo().getResourceLoader().getResource(path);
        if (resource == null) {
            resp.setStatus(404);
            return;
        } else if (resource.isDirectory()) {
            handleWelcomePage(req, resp, resource);
        } else {
            InputStream in = new BufferedInputStream(new FileInputStream(resource));
            try {
                int read;
                final byte[] buffer = new byte[1024];
                out = resp.getOutputStream();
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } finally {
                if (out != null) {
                    IoUtils.safeClose(out);
                }
                IoUtils.safeClose(in);
            }
        }
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        if (!isAllowed(exchange.getRelativePath())) {
            exchange.setResponseCode(404);
            completionHandler.handleComplete();
            return;
        }
        File resource = deployment.getDeploymentInfo().getResourceLoader().getResource(exchange.getRelativePath());
        if (resource == null) {
            exchange.setResponseCode(404);
            completionHandler.handleComplete();
            return;
        } else if (resource.isDirectory()) {
            handleWelcomePage(exchange, completionHandler, resource);
        } else {
            fileCache.serveFile(exchange, completionHandler, resource);
        }
    }

    private void handleWelcomePage(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final File resource) {
        final String found = findWelcomeResource(resource, exchange.getRelativePath().endsWith("/") ? exchange.getRelativePath() : exchange.getRelativePath() + "/");
        if (found != null) {
            exchange.setResponseCode(302);
            StringBuilder newLocation = new StringBuilder(exchange.getRequestURL());
            if (newLocation.charAt(newLocation.length() - 1) != '/') {
                newLocation.append('/');
            }
            newLocation.append(found);
            exchange.getResponseHeaders().put(Headers.LOCATION, newLocation.toString());
            completionHandler.handleComplete();
        } else {
            exchange.setResponseCode(404);
            completionHandler.handleComplete();
        }
    }

    private void handleWelcomePage(final HttpServletRequest req, final HttpServletResponse resp, final File resource) {
        final String found = findWelcomeResource(resource, req.getPathInfo().endsWith("/") ? req.getPathInfo() : req.getPathInfo() + "/");
        if (found != null) {
            resp.setStatus(302);
            StringBuffer newLocation = req.getRequestURL();
            if (newLocation.charAt(newLocation.length() - 1) != '/') {
                newLocation.append('/');
            }
            newLocation.append(found);
            resp.addHeader(Headers.LOCATION_STRING, newLocation.toString());
        } else {
            resp.setStatus(404);
        }
    }

    private String findWelcomeResource(final File resource, final String path) {
        for (String i : welcomePages) {
            final File res = new File(resource + File.separator + i);
            if (res.exists()) {
                return i;
            }
        }

        for (String i : welcomePages) {
            final ServletInitialHandler handler = deployment.getServletPaths().getServletHandlerByPath(path + i).getHandler();
            if(handler.getServletInfo() != null) {
                return i;
            }
        }
        return null;
    }

    private String getPath(final HttpServletRequest request) {
        String result = request.getPathInfo();
        if (result == null) {
            result = request.getServletPath();
        }
        if ((result == null) || (result.equals(""))) {
            result = "/";
        }
        return result;
    }

    private boolean isAllowed(String path) {
        int pos = path.lastIndexOf('/');
        final String lastSegment;
        if (pos == -1) {
            lastSegment = path;
        } else {
            lastSegment = path.substring(pos + 1);
        }
        if(lastSegment.isEmpty()) {
            return true;
        }
        int ext = lastSegment.lastIndexOf('.');
        if (ext == -1) {
            return defaultAllowed;
        }
        final String extension = lastSegment.substring(ext + 1, lastSegment.length());
        if (defaultAllowed) {
            return !disallowed.contains(extension);
        } else {
            return allowed.contains(extension);
        }
    }

    public Set<String> getAllowed() {
        return allowed;
    }

    public Set<String> getDisallowed() {
        return disallowed;
    }

    public boolean isDefaultAllowed() {
        return defaultAllowed;
    }

    public void setDefaultAllowed(final boolean defaultAllowed) {
        this.defaultAllowed = defaultAllowed;
    }

    public FileCache getFileCache() {
        return fileCache;
    }

    public void setFileCache(final FileCache fileCache) {
        this.fileCache = fileCache;
    }
}
