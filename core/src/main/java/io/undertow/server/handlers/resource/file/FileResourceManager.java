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

package io.undertow.server.handlers.resource.file;

import java.io.File;

import io.undertow.UndertowMessages;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceManager;
/**
 * Serves files from the file system.
 */
public class FileResourceManager implements ResourceManager {

    private volatile File base;

    public FileResourceManager(final File base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        this.base = base;
    }

    public File getBase() {
        return base;
    }

    public FileResourceManager setBase(final File base) {
        if (base == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("base");
        }
        this.base = base;
        return this;
    }

    public Resource getResource(final String p) {
        String path = p;
        if (File.separatorChar != '/') {
            if (path.indexOf(File.separatorChar) != -1) {
                return null;
            }
            path = path.replace('/', File.separatorChar);
        }

        final File file = new File(base, path);
        if(file.exists()) {
            return new FileResource(file);
        } else {
            return null;
        }
    }
}
