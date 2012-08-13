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
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
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
import io.undertow.servlet.api.ResourceLoader;
import io.undertow.util.CopyOnWriteMap;
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

    private final ResourceLoader resourceLoader;
    private volatile FileCache fileCache = DirectFileCache.INSTANCE;

    private volatile boolean defaultAllowed = false;

    private final Set<String> allowed = Collections.newSetFromMap(new CopyOnWriteMap<String, Boolean>());
    private final Set<String> disallowed = Collections.newSetFromMap(new CopyOnWriteMap<String, Boolean>());

    public DefaultServlet(final ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
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
        URL resource = resourceLoader.getResource(path);
        if (resource == null) {
            resp.setStatus(404);
            return;
        }
        int read;
        final byte[] buffer = new byte[1024];
        BufferedInputStream in = null;
        ServletOutputStream out = null;
        try {
            out = resp.getOutputStream();
            in = new BufferedInputStream(new FileInputStream(resource.getFile()));
            while ((read = in.read(buffer)) != 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            if (out != null) {
                IoUtils.safeClose(out);
            }
            if (in != null) {
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
        URL resource = resourceLoader.getResource(exchange.getRelativePath());
        if (resource == null) {
            exchange.setResponseCode(404);
            completionHandler.handleComplete();
            return;
        }
        fileCache.serveFile(exchange, completionHandler, new File(resource.getFile()));
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
        int ext = path.lastIndexOf('.');
        if (ext == -1) {
            return defaultAllowed;
        }
        final String extension = path.substring(ext + 1, path.length());
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
