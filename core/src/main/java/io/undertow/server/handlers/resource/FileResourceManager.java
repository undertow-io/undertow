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

package io.undertow.server.handlers.resource;

import io.undertow.UndertowMessages;

import java.io.File;

/**
 * Serves files from the file system.
 */
public class FileResourceManager extends PathResourceManager {

    public FileResourceManager(final File base) {
        this(base, 1024, true, false, null);
    }
    public FileResourceManager(final File base, long transferMinSize) {
        this(base, transferMinSize, true, false, null);
    }

    public FileResourceManager(final File base, long transferMinSize, boolean caseSensitive) {
        this(base, transferMinSize, caseSensitive, false, null);
    }

    public FileResourceManager(final File base, long transferMinSize, boolean followLinks, final String... safePaths) {
        this(base, transferMinSize, true, followLinks, safePaths);
    }

    protected FileResourceManager(long transferMinSize, boolean caseSensitive, boolean followLinks, final String... safePaths) {
        super(transferMinSize, caseSensitive, followLinks, safePaths);
    }

    public FileResourceManager(final File base, long transferMinSize, boolean caseSensitive, boolean followLinks, final String... safePaths) {
        super(base.toPath(), transferMinSize, caseSensitive, followLinks, safePaths);
    }

    public File getBase() {
        return new File(base);
    }

    public FileResourceManager setBase(final File base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        String basePath = base.getAbsolutePath();
        if (!basePath.endsWith("/")) {
            basePath = basePath + '/';
        }
        this.base = basePath;
        return this;
    }
}
