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

import java.io.IOException;
import java.net.URL;

/**
 * @author Stuart Douglas
 */
public class ClassPathResourceManager implements ResourceManager {

    /**
     * The class loader that is used to load resources
     */
    private final ClassLoader classLoader;
    /**
     * The prefix that is appended to resources that are to be loaded.
     */
    private final String prefix;

    public ClassPathResourceManager(final ClassLoader loader, final Package p) {
        this(loader, p.getName().replace(".", "/"));
    }

    public ClassPathResourceManager(final ClassLoader classLoader, final String prefix) {
        this.classLoader = classLoader;
        if (prefix.isEmpty()) {
            this.prefix = "";
        } else if (prefix.endsWith("/")) {
            this.prefix = prefix;
        } else {
            this.prefix = prefix + "/";
        }
    }

    public ClassPathResourceManager(final ClassLoader classLoader) {
        this(classLoader, "");
    }

    @Override
    public Resource getResource(final String path) throws IOException {
        if( path == null ) {
            return null;
        }
        String modPath = path;
        if(modPath.startsWith("/")) {
            modPath = path.substring(1);
        }
        final String realPath = prefix + modPath;
        final URL resource = classLoader.getResource(realPath);
        if(resource == null) {
            return null;
        } else {
            return new URLResource(resource, path);
        }

    }

    @Override
    public boolean isResourceChangeListenerSupported() {
        return false;
    }

    @Override
    public void registerResourceChangeListener(ResourceChangeListener listener) {
        throw UndertowMessages.MESSAGES.resourceChangeListenerNotSupported();
    }

    @Override
    public void removeResourceChangeListener(ResourceChangeListener listener) {
        throw UndertowMessages.MESSAGES.resourceChangeListenerNotSupported();
    }


    @Override
    public void close() throws IOException {
    }
}
