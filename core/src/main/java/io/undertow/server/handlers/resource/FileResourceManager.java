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

package io.undertow.server.handlers.resource;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;

/**
 * Serves files from the file system.
 */
public class FileResourceManager implements ResourceManager {

    private volatile Path base;

    public FileResourceManager(final Path base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        this.base = base;
    }

    public Path getBase() {
        return base;
    }

    public FileResourceManager setBase(final Path base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        this.base = base;
        return this;
    }

    public Resource getResource(final String p) {
        String path = p;
        if (p.startsWith("/")) {
            path = p.substring(1);
        }
        try {
            Path file = base.resolve(path);
            if (Files.exists(file)) {
                return new FileResource(file);
            } else {
                return null;
            }
        } catch (InvalidPathException e) {
            UndertowLogger.REQUEST_LOGGER.debugf(e, "Invalid path %s");
            return null;
        }
    }
}
