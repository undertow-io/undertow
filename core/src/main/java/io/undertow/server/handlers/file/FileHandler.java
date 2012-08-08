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

package io.undertow.server.handlers.file;

import java.io.File;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * Serves files direct from the file system.
 *
 * @author Stuart Douglas
 */
public class FileHandler implements HttpHandler {

    private volatile File base;
    private volatile FileCache fileCache = DirectFileCache.INSTANCE;

    public FileHandler(final File base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        this.base = base;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        String path = exchange.getRelativePath();
        if (File.separatorChar != '/') {
            if (path.indexOf(File.separatorChar) != -1) {
                exchange.setResponseCode(404);
                completionHandler.handleComplete();
                return;
            }
            path = path.replace('/', File.separatorChar);
        }
        fileCache.serveFile(exchange, completionHandler, new File(base, path));
    }

    public File getBase() {
        return base;
    }

    public void setBase(final File base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        this.base = base;
    }

    public FileCache getFileCache() {
        return fileCache;
    }

    public void setFileCache(final FileCache fileCache) {
        if (fileCache == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        this.fileCache = fileCache;
    }
}
